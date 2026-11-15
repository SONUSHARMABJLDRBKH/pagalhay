package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RoyalBulletDatabase
import com.example.data.repository.RoyalBulletRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoyalBulletRepositoryTest {

    @Test
    fun `full wallet order referral and withdrawal verification rules`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = RoyalBulletDatabase.getInstance(context)
        val dao = db.appDao()
        val repo = RoyalBulletRepository(context)
        repo.ensureSeeded()

        // 1. Register User A -> ₹90 Welcome Bonus credited immediately to Account Balance (availableBalance)
        val regA = repo.registerUser("9876543210", "Pass@123", "1122", "")
        assertTrue(regA.isSuccess)
        val userA = regA.getOrThrow()
        assertEquals(90.0, userA.availableBalance, 0.01)
        assertTrue(userA.welcomeBonusClaimed)

        // Cannot claim welcome bonus twice
        assertTrue(repo.claimWelcomeBonus(userA.id).isFailure)

        // 2. Register User B with User A's invite code -> User B gets ₹90; User A gets ₹0 referral reward before purchases
        val regB = repo.registerUser("9876543211", "Pass@123", "3344", userA.inviteCode)
        assertTrue(regB.isSuccess)
        val userB = regB.getOrThrow()
        assertEquals(90.0, userB.availableBalance, 0.01)
        assertEquals(90.0, dao.getUserById(userA.id)!!.availableBalance, 0.01)

        // 3. Withdrawal below ₹150 fails, and ₹150 fails when balance is only ₹90
        assertTrue(
            repo.submitWithdrawalRequest(userA.id, 100.0, "UPI", "rider@upi", "", "Rider A", "", "1122").isFailure
        )
        assertTrue(
            repo.submitWithdrawalRequest(userA.id, 150.0, "UPI", "rider@upi", "", "Rider A", "", "1122").isFailure
        )

        // 4. Verified UTR credits depositBalance immediately as Successful; failed attempts record Failed without crediting
        val failedRec = repo.recordFailedRecharge(
            userA.id, 1000.0, "PhonePe", "BADREF1", "Screenshot amount mismatch"
        )
        assertEquals("Failed", failedRec?.status)
        assertEquals(0.0, dao.getUserById(userA.id)!!.depositBalance, 0.01)

        val recA = repo.submitRechargeRequest(userA.id, 1000.0, "PhonePe", "431892041155").getOrThrow()
        assertEquals("Successful", recA.status)
        assertEquals(1000.0, dao.getUserById(userA.id)!!.depositBalance, 0.01)

        // Duplicate UTR is rejected
        assertTrue(repo.submitRechargeRequest(userA.id, 1000.0, "PhonePe", "431892041155").isFailure)

        val recB = repo.submitRechargeRequest(userB.id, 1000.0, "Paytm", "431892049988").getOrThrow()
        assertEquals("Successful", recB.status)
        assertEquals(1000.0, dao.getUserById(userB.id)!!.depositBalance, 0.01)

        // 5. Both User A and User B order Product 1 (₹700) without delivery address and Admin fulfills both
        val orderA = repo.placeProductOrder(userA.id, "prod_1", "", false).getOrThrow()
        assertTrue(repo.adminFulfillOrder(orderA.id).isSuccess)
        // User B hasn't fulfilled yet -> still ₹90 (₹0 referral reward)
        assertEquals(90.0, dao.getUserById(userA.id)!!.availableBalance, 0.01)

        val orderB = repo.placeProductOrder(userB.id, "prod_1", "", false).getOrThrow()
        assertTrue(repo.adminFulfillOrder(orderB.id).isSuccess)

        // Now User A receives ₹85 one-time (90 + 85 = 175); User B receives ₹0 referral commission (stays 90)
        assertEquals(175.0, dao.getUserById(userA.id)!!.availableBalance, 0.01)
        assertEquals(90.0, dao.getUserById(userB.id)!!.availableBalance, 0.01)

        // 6. Now User A can withdraw ₹150 (>= min ₹150)
        val wSuccess = repo.submitWithdrawalRequest(
            userA.id, 150.0, "UPI", "rider@upi", "", "Rider A", "", "1122"
        )
        assertTrue(wSuccess.isSuccess)
        assertEquals(25.0, dao.getUserById(userA.id)!!.availableBalance, 0.01)
    }

    @Test
    fun `plan timers initialize between 24h and 80h and rotate prices with proportional daily income`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = RoyalBulletDatabase.getInstance(context)
        val dao = db.appDao()
        val repo = RoyalBulletRepository(context)
        repo.ensureSeeded()

        val initialProducts = dao.observeActiveProducts().first().associateBy { it.id }
        assertEquals(700.0, initialProducts["prod_1"]!!.price, 0.01)
        assertEquals(1500.0, initialProducts["prod_2"]!!.price, 0.01)
        assertEquals(2000.0, initialProducts["prod_3"]!!.price, 0.01)
        assertEquals(3000.0, initialProducts["prod_4"]!!.price, 0.01)

        initialProducts.values.forEach { p ->
            assertTrue("Expected ${p.id} rotationHours in 24..80", p.rotationHours in 24..80)
            assertTrue("Expected ${p.id} nextSwitchAt > 0", p.nextSwitchAt > 0L)
        }

        val initialVip = dao.observeMembershipPlans().first().associateBy { it.id }
        initialVip.values.forEach { v ->
            assertTrue("Expected ${v.id} rotationHours in 24..80", v.rotationHours in 24..80)
            assertTrue("Expected ${v.id} nextSwitchAt > 0", v.nextSwitchAt > 0L)
        }

        // Advance time just past each initial product's switch deadline (1 step forward)
        val p1Next = initialProducts["prod_1"]!!.nextSwitchAt + 1000L
        repo.syncPlanRotations(p1Next)
        assertEquals(900.0, dao.getProductById("prod_1")!!.price, 0.01)
        assertEquals(155.0, dao.getProductById("prod_1")!!.dailyEarning, 0.01)

        val p2Next = initialProducts["prod_2"]!!.nextSwitchAt + 1000L
        repo.syncPlanRotations(p2Next)
        assertEquals(1800.0, dao.getProductById("prod_2")!!.price, 0.01)
        assertEquals(300.0, dao.getProductById("prod_2")!!.dailyEarning, 0.01)

        val p3Next = initialProducts["prod_3"]!!.nextSwitchAt + 1000L
        repo.syncPlanRotations(p3Next)
        assertEquals(4000.0, dao.getProductById("prod_3")!!.price, 0.01)
        assertEquals(690.0, dao.getProductById("prod_3")!!.dailyEarning, 0.01)

        val p4Next = initialProducts["prod_4"]!!.nextSwitchAt + 1000L
        repo.syncPlanRotations(p4Next)
        assertEquals(5500.0, dao.getProductById("prod_4")!!.price, 0.01)
        assertEquals(960.0, dao.getProductById("prod_4")!!.dailyEarning, 0.01)

        // VIP cards also rotate after their timer ends
        val vip1Next = initialVip["vip_1"]!!.nextSwitchAt + 1000L
        repo.syncPlanRotations(vip1Next)
        assertEquals(1050.0, dao.getMembershipPlanById("vip_1")!!.price, 0.01)

        // Merchant UPI ID defaults to 7544985606@ybl and dynamic UPI URI carries exact recharge amount
        val cfg = dao.getAppConfig()!!
        assertEquals("7544985606@ybl", cfg.upiMerchantId)
        val uri700 = com.example.ui.components.UpiQrGenerator.buildUpiPaymentUri(cfg.upiMerchantId, amount = 700.0)
        assertTrue(uri700.contains("pa=7544985606@ybl"))
        assertTrue(uri700.contains("am=700.00"))
        val uri1500 = com.example.ui.components.UpiQrGenerator.buildUpiPaymentUri(cfg.upiMerchantId, amount = 1500.0)
        assertTrue(uri1500.contains("am=1500.00"))
    }

    @Test
    fun `share url defaults to babu apk and update checker detects newer builds`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = RoyalBulletDatabase.getInstance(context)
        val dao = db.appDao()
        val repo = RoyalBulletRepository(context)
        repo.ensureSeeded()

        val expectedShareUrl =
            "https://github.com/SONUSHARMABJLDRBKH/babu/releases/download/debug-apk-build-1-1/babu-debug-build-1.apk"
        val cfg = dao.getAppConfig()!!
        assertEquals(expectedShareUrl, cfg.appInstallUrl)

        // Updating share URL preserves existing merchant UPI ID
        val updatedUrl =
            "https://github.com/SONUSHARMABJLDRBKH/babu/releases/download/debug-apk-build-2-1/babu-debug-build-2.apk"
        repo.updateAppInstallUrlIfDifferent(updatedUrl)
        val afterUpdate = dao.getAppConfig()!!
        assertEquals(updatedUrl, afterUpdate.appInstallUrl)
        assertEquals("7544985606@ybl", afterUpdate.upiMerchantId)

        // AppUpdateChecker detects run #2 and semantic v1.5+ without looping on build 1 baseline
        assertTrue(
            com.example.data.remote.AppUpdateChecker.isRemoteNewer(
                "debug-apk-build-2-1",
                updatedUrl,
                "1.4"
            )
        )
        assertTrue(
            com.example.data.remote.AppUpdateChecker.isRemoteNewer(
                "v1.5",
                updatedUrl,
                "1.4"
            )
        )
        assertTrue(
            !com.example.data.remote.AppUpdateChecker.isRemoteNewer(
                "debug-apk-build-1-1",
                expectedShareUrl,
                "1.4"
            )
        )

        // All 4 uploaded QR codes rotate one-by-one every time (0 -> 1 -> 2 -> 3 -> 0)
        val first = com.example.ui.components.UpiQrGenerator.nextScannerIndex(context)
        val second = com.example.ui.components.UpiQrGenerator.nextScannerIndex(context)
        val third = com.example.ui.components.UpiQrGenerator.nextScannerIndex(context)
        val fourth = com.example.ui.components.UpiQrGenerator.nextScannerIndex(context)
        val fifth = com.example.ui.components.UpiQrGenerator.nextScannerIndex(context)
        assertEquals(0, first)
        assertEquals(1, second)
        assertEquals(2, third)
        assertEquals(3, fourth)
        assertEquals(0, fifth)
        assertEquals(
            R.drawable.qr_code_1,
            com.example.ui.components.UpiQrGenerator.scannerDrawableForIndex(first)
        )
        assertEquals(
            R.drawable.qr_code_2,
            com.example.ui.components.UpiQrGenerator.scannerDrawableForIndex(second)
        )
        assertEquals(
            R.drawable.qr_code_3,
            com.example.ui.components.UpiQrGenerator.scannerDrawableForIndex(third)
        )
        assertEquals(
            R.drawable.qr_code_4,
            com.example.ui.components.UpiQrGenerator.scannerDrawableForIndex(fourth)
        )

        // Royal Bullet 350 Wallet chart reflects ₹700 deposit UP (Yellow) and ₹200 withdrawal DOWN (Red)
        val candles = com.example.ui.screens.wallet.buildWalletMarketCandles(
            recharges = listOf(
                com.example.data.local.RechargeEntity(
                    id = "r1",
                    userId = "u1",
                    userMobile = "9876543210",
                    amount = 700.0,
                    method = "PhonePe",
                    referenceId = "431892041155",
                    status = "Successful",
                    createdAt = 1000L,
                    verifiedAt = 1000L
                )
            ),
            withdrawals = listOf(
                com.example.data.local.WithdrawalEntity(
                    id = "w1",
                    userId = "u1",
                    userMobile = "9876543210",
                    amount = 200.0,
                    feeAmount = 4.0,
                    netAmount = 196.0,
                    payoutMethod = "UPI",
                    upiId = "test@upi",
                    bankAccountNumber = "",
                    accountHolderName = "Rider",
                    ifscCode = "",
                    status = "Paid",
                    createdAt = 2000L,
                    processedAt = 2000L
                )
            )
        )
        val realMoves = candles.filter { it.isRealTx }
        assertEquals(2, realMoves.size)
        assertTrue(realMoves[0].isUp)
        assertEquals(700.0, realMoves[0].delta, 0.01)
        assertTrue(!realMoves[1].isUp)
        assertEquals(-200.0, realMoves[1].delta, 0.01)
    }
}
