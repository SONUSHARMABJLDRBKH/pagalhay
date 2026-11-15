package com.example.ui.screens.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AppConfigEntity
import com.example.data.local.BankCardEntity
import com.example.data.local.RechargeEntity
import com.example.data.local.UserEntity
import com.example.data.local.WithdrawalEntity
import com.example.data.repository.RoyalBulletRepository
import com.example.ui.components.BlueGradientButton
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoyalCard
import com.example.ui.components.RoyalTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.TransparentRuleBanner
import com.example.ui.components.UpiQrGenerator
import com.example.ui.components.formatInr
import com.example.ui.components.formatTimestamp
import com.example.ui.strings.LocalStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedBg
import com.example.ui.theme.IceBackground
import com.example.ui.theme.RoyalBlueDeep
import com.example.ui.theme.RoyalBluePrimary
import com.example.ui.theme.RoyalSkyBorder
import com.example.ui.theme.RoyalSkyLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import kotlinx.coroutines.delay
import java.util.Locale

private fun launchUpiPaymentApp(
    context: Context,
    channel: String,
    merchantUpiId: String,
    @DrawableRes scannerResId: Int,
    amount: Double,
    onShowMessage: (String) -> Unit
) {
    if (amount < RoyalBulletRepository.MIN_RECHARGE) {
        onShowMessage("Enter amount ₹${RoyalBulletRepository.MIN_RECHARGE.toInt()} or more before opening $channel.")
        return
    }
    val upiUri = Uri.parse(
        UpiQrGenerator.resolvePaymentUriForScanner(
            context = context,
            drawableResId = scannerResId,
            fallbackMerchantUpiId = merchantUpiId,
            amount = amount
        )
    )
    val packageName = when (channel) {
        "PhonePe" -> "com.phonepe.app"
        "Paytm" -> "net.one97.paytm"
        "Google Pay" -> "com.google.android.apps.nbu.paisa.user"
        else -> null
    }
    val targetedIntent = Intent(Intent.ACTION_VIEW, upiUri).apply {
        if (packageName != null) setPackage(packageName)
    }
    val launchedTarget = runCatching {
        context.startActivity(targetedIntent)
    }.isSuccess

    if (!launchedTarget) {
        val fallbackIntent = Intent.createChooser(
            Intent(Intent.ACTION_VIEW, upiUri),
            "Pay ₹${amount.toInt()} via UPI App"
        )
        runCatching {
            context.startActivity(fallbackIntent)
        }.onFailure {
            onShowMessage("No UPI app installed. Scan the ₹${amount.toInt()} QR code below using PhonePe/Paytm/GPay.")
        }
    }
}

private fun formatGatewayTimer(secondsLeft: Int): String {
    val safe = secondsLeft.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RechargeScreen(
    user: UserEntity?,
    config: AppConfigEntity,
    recharges: List<RechargeEntity>,
    isProcessingPayment: Boolean = false,
    lastSuccessfulRecharge: RechargeEntity? = null,
    lastFailedRechargeReason: String? = null,
    onDismissSuccessCard: () -> Unit = {},
    onUpdateMerchantUpi: (String) -> Unit = {},
    onBack: () -> Unit,
    onSubmitRecharge: (amount: Double, method: String, referenceId: String, screenshotUri: Uri?) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var amountText by remember { mutableStateOf("700") }
    var selectedMethod by remember { mutableStateOf("PhonePe") }
    var utrReference by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }

    var isGatewayStep by remember { mutableStateOf(false) }
    var gatewaySessionId by remember { mutableIntStateOf(0) }
    var gatewaySecondsLeft by remember { mutableIntStateOf(300) }
    var activeScannerIndex by remember { mutableIntStateOf(0) }
    val activeScannerResId = UpiQrGenerator.scannerDrawableForIndex(activeScannerIndex)

    BackHandler(enabled = isGatewayStep) {
        if (!isProcessingPayment) {
            isGatewayStep = false
        }
    }

    LaunchedEffect(isGatewayStep, gatewaySessionId) {
        if (isGatewayStep) {
            while (gatewaySecondsLeft > 0) {
                delay(1000L)
                if (!isProcessingPayment) {
                    gatewaySecondsLeft = (gatewaySecondsLeft - 1).coerceAtLeast(0)
                }
            }
        }
    }

    LaunchedEffect(lastSuccessfulRecharge?.id) {
        if (lastSuccessfulRecharge != null) {
            isGatewayStep = false
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            screenshotUri = uri
        }
    }

    DisposableEffect(Unit) {
        onDispose { onDismissSuccessCard() }
    }

    val currentAmount = (amountText.toDoubleOrNull() ?: 700.0).coerceAtLeast(1.0)
    val presets = listOf(700, 1000, 1500, 2000, 3000, 5000, 10000)
    val upiApps = listOf("PhonePe", "Paytm", "Google Pay")

    Scaffold(
        topBar = {
            RoyalTopBar(
                title = if (isGatewayStep) "Payment Gateway" else s.recharge,
                onBack = {
                    if (isGatewayStep) {
                        if (!isProcessingPayment) isGatewayStep = false
                    } else {
                        onBack()
                    }
                }
            )
        },
        containerColor = IceBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isGatewayStep && lastSuccessfulRecharge != null) {
                item {
                    Surface(
                        color = SuccessGreenBg,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SuccessGreen.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .testTag("recharge_success_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Success",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Payment Successful!",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = SuccessGreen
                                    )
                                }
                                StatusPill("Successful")
                            }
                            Text(
                                text = "${formatInr(lastSuccessfulRecharge.amount)} verified and added to your Wallet via ${lastSuccessfulRecharge.method}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "UTR: ${lastSuccessfulRecharge.referenceId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (!lastFailedRechargeReason.isNullOrBlank()) {
                item {
                    Surface(
                        color = ErrorRedBg,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ErrorRed.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .testTag("recharge_failed_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = "Failed",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Payment Failed",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ErrorRed
                                    )
                                }
                                StatusPill("Failed")
                            }
                            Text(
                                text = lastFailedRechargeReason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            if (!isGatewayStep) {
                // STEP 1: Amount selection only — NO QR code visible before clicking Recharge
                item {
                    RoyalCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current Wallet Balance", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text(
                                    formatInr(user?.depositBalance ?: 0.0),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RoyalBluePrimary
                                )
                            }

                            Text("1. Select Recharge Amount (₹700 – ₹1,00,000)", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presets.forEach { preset ->
                                    FilterChip(
                                        selected = amountText == preset.toString(),
                                        onClick = { amountText = preset.toString() },
                                        label = { Text("₹$preset") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = RoyalBluePrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Recharge Amount (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recharge_amount_input")
                            )

                            Text("2. Select Preferred Payment App", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                upiApps.forEach { appName ->
                                    val isSelected = selectedMethod == appName
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedMethod = appName },
                                        label = { Text(appName, maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = RoyalBluePrimary,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            BlueGradientButton(
                                text = "Recharge • ${formatInr(currentAmount)}",
                                onClick = {
                                    val amt = amountText.toDoubleOrNull() ?: 0.0
                                    if (amt < RoyalBulletRepository.MIN_RECHARGE || amt > RoyalBulletRepository.MAX_RECHARGE) {
                                        onShowMessage(
                                            "Recharge amount must be between ₹${RoyalBulletRepository.MIN_RECHARGE.toInt()} and ₹${RoyalBulletRepository.MAX_RECHARGE.toInt()}."
                                        )
                                    } else {
                                        onDismissSuccessCard()
                                        activeScannerIndex = UpiQrGenerator.nextScannerIndex(context)
                                        utrReference = ""
                                        screenshotUri = null
                                        gatewaySecondsLeft = 300
                                        gatewaySessionId += 1
                                        isGatewayStep = true
                                    }
                                },
                                testTag = "recharge_proceed_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Text("Recharge History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }

                if (recharges.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Wallet,
                            title = "No Recharges Yet",
                            subtitle = "Verified deposits appear here as Successful; invalid screenshots appear as Failed."
                        )
                    }
                } else {
                    items(recharges, key = { it.id }) { item ->
                        RoyalCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${formatInr(item.amount)} • ${item.method}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "UTR: ${item.referenceId}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = TextSecondary
                                    )
                                    if (item.note.isNotBlank()) {
                                        Text(
                                            item.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (item.status == "Failed") ErrorRed else TextMuted
                                        )
                                    }
                                    Text(
                                        formatTimestamp(item.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                StatusPill(item.status)
                            }
                        }
                    }
                }
            } else {
                // STEP 2: Payment Gateway Redirect View (Rotating QR + 5-min Countdown Timer + UTR + Screenshot)
                val isExpired = gatewaySecondsLeft <= 0
                val timerBg = when {
                    isExpired || gatewaySecondsLeft <= 30 -> ErrorRedBg
                    gatewaySecondsLeft <= 60 -> WarningAmberBg
                    else -> SuccessGreenBg
                }
                val timerFg = when {
                    isExpired || gatewaySecondsLeft <= 30 -> ErrorRed
                    gatewaySecondsLeft <= 60 -> WarningAmber
                    else -> SuccessGreen
                }

                item {
                    RoyalCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Payment Gateway Header with Live 5-Min Timer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "UPI Payment Gateway",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = RoyalBlueDeep
                                    )
                                    Text(
                                        text = "Payable Amount: ${formatInr(currentAmount)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = RoyalBluePrimary
                                    )
                                }
                                Surface(
                                    color = timerBg,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .border(1.dp, timerFg.copy(alpha = 0.4f), RoundedCornerShape(50))
                                        .testTag("gateway_timer_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Timer,
                                            contentDescription = "Gateway countdown timer",
                                            tint = timerFg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = formatGatewayTimer(gatewaySecondsLeft),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = timerFg
                                        )
                                    }
                                }
                            }

                            // QR Code Card with Side/Top Timer
                            Surface(
                                color = RoyalSkyLight,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, RoyalSkyBorder, RoundedCornerShape(14.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Scan Dynamic UPI QR (${formatInr(currentAmount)})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = RoyalBlueDeep
                                            )
                                            Text(
                                                "Expires in ${formatGatewayTimer(gatewaySecondsLeft)} • Verified Gateway QR",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                color = TextSecondary
                                            )
                                        }
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.border(1.dp, RoyalSkyBorder, RoundedCornerShape(50))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Lock,
                                                    contentDescription = "Protected UPI QR",
                                                    tint = RoyalBluePrimary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Secure QR",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = RoyalBluePrimary
                                                )
                                            }
                                        }
                                    }

                                    if (isExpired) {
                                        Surface(
                                            color = ErrorRedBg,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, ErrorRed.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                                .testTag("gateway_expired_banner")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ErrorOutline,
                                                    contentDescription = "Session expired",
                                                    tint = ErrorRed,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                                Text(
                                                    text = "QR Payment Session Expired (00:00)",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = ErrorRed
                                                )
                                                Text(
                                                    text = "The 5-minute payment window has ended. Tap below to load a fresh QR code.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextPrimary
                                                )
                                                BlueGradientButton(
                                                    text = "Generate New QR (5:00)",
                                                    onClick = {
                                                        onDismissSuccessCard()
                                                        activeScannerIndex = UpiQrGenerator.nextScannerIndex(context)
                                                        gatewaySecondsLeft = 300
                                                        gatewaySessionId += 1
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    } else {
                                        DynamicUpiQrCode(
                                            scannerResId = activeScannerResId,
                                            scannerNumber = activeScannerIndex + 1,
                                            amount = currentAmount
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            upiApps.forEach { appName ->
                                                val isSelected = selectedMethod == appName
                                                val containerColor = when (appName) {
                                                    "PhonePe" -> if (isSelected) Color(0xFF5F259F) else Color(0xFFF3E8FF)
                                                    "Paytm" -> if (isSelected) Color(0xFF00BAF2) else Color(0xFFE0F7FE)
                                                    else -> if (isSelected) Color(0xFF1A73E8) else Color(0xFFE8F0FE)
                                                }
                                                val contentColor = if (isSelected) Color.White else RoyalBlueDeep

                                                Button(
                                                    onClick = {
                                                        selectedMethod = appName
                                                        launchUpiPaymentApp(
                                                            context = context,
                                                            channel = appName,
                                                            merchantUpiId = config.upiMerchantId,
                                                            scannerResId = activeScannerResId,
                                                            amount = currentAmount,
                                                            onShowMessage = onShowMessage
                                                        )
                                                    },
                                                    enabled = !isProcessingPayment,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = containerColor,
                                                        contentColor = contentColor
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("pay_app_${appName.lowercase().replace(" ", "_")}")
                                                ) {
                                                    Text(
                                                        text = appName,
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val saved = UpiQrGenerator.saveScannerQrToGallery(
                                                        context = context,
                                                        drawableResId = activeScannerResId,
                                                        amount = currentAmount
                                                    )
                                                    if (saved) {
                                                        onShowMessage("₹${currentAmount.toInt()} QR saved to Gallery! Open PhonePe/Paytm/GPay → Scan → Gallery.")
                                                    } else {
                                                        val shared = UpiQrGenerator.shareScannerQrImage(
                                                            context = context,
                                                            drawableResId = activeScannerResId,
                                                            amount = currentAmount
                                                        )
                                                        if (!shared) {
                                                            onShowMessage("Could not export QR image.")
                                                        }
                                                    }
                                                },
                                                enabled = !isProcessingPayment,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("recharge_save_qr_button")
                                            ) {
                                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Save QR", maxLines = 1)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val pkg = when (selectedMethod) {
                                                        "PhonePe" -> "com.phonepe.app"
                                                        "Paytm" -> "net.one97.paytm"
                                                        "Google Pay" -> "com.google.android.apps.nbu.paisa.user"
                                                        else -> null
                                                    }
                                                    val ok = UpiQrGenerator.shareScannerQrImage(
                                                        context = context,
                                                        drawableResId = activeScannerResId,
                                                        amount = currentAmount,
                                                        targetPackage = pkg
                                                    ) || UpiQrGenerator.shareScannerQrImage(
                                                        context = context,
                                                        drawableResId = activeScannerResId,
                                                        amount = currentAmount,
                                                        targetPackage = null
                                                    )
                                                    if (!ok) {
                                                        onShowMessage("Could not share QR image.")
                                                    }
                                                },
                                                enabled = !isProcessingPayment,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("recharge_share_qr_button")
                                            ) {
                                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Send QR to App", maxLines = 1)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                launchUpiPaymentApp(
                                                    context = context,
                                                    channel = selectedMethod,
                                                    merchantUpiId = config.upiMerchantId,
                                                    scannerResId = activeScannerResId,
                                                    amount = currentAmount,
                                                    onShowMessage = onShowMessage
                                                )
                                            },
                                            enabled = !isProcessingPayment,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open $selectedMethod & Pay ${formatInr(currentAmount)}")
                                        }
                                    }
                                }
                            }

                            // Directly Below QR Code: 12-Digit UTR + Payment Screenshot Upload
                            Surface(
                                color = SuccessGreenBg,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SuccessGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Enter 12-Digit UTR & Payment Screenshot",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = SuccessGreen
                                        )
                                    }

                                    OutlinedTextField(
                                        value = utrReference,
                                        onValueChange = { utrReference = it.uppercase().trim() },
                                        label = { Text("Enter 12-Digit UTR / Transaction Ref No.") },
                                        placeholder = { Text("e.g. 431892041155") },
                                        enabled = !isProcessingPayment && !isExpired,
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("recharge_utr_input")
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            screenshotPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !isProcessingPayment && !isExpired,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("recharge_pick_screenshot_button")
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (screenshotUri == null) "Select Payment Screenshot from Gallery"
                                            else "Change Payment Screenshot"
                                        )
                                    }

                                    if (screenshotUri != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White)
                                                .border(1.dp, SuccessGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = screenshotUri,
                                                contentDescription = "Selected payment screenshot preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Payment Screenshot Attached",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = SuccessGreen
                                                )
                                                Text(
                                                    "Will verify exact ₹${currentAmount.toInt()} payment receipt",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                            TextButton(
                                                onClick = { screenshotUri = null },
                                                enabled = !isProcessingPayment
                                            ) {
                                                Text("Remove", color = ErrorRed)
                                            }
                                        }
                                    }
                                }
                            }

                            TransparentRuleBanner(
                                "Payment succeeds ONLY when your UTR and uploaded Gallery screenshot match the exact ₹${currentAmount.toInt()} payment. Mismatch or wrong image will fail payment."
                            )

                            if (isProcessingPayment) {
                                Surface(
                                    color = RoyalSkyLight,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, RoyalSkyBorder, RoundedCornerShape(14.dp))
                                        .testTag("recharge_processing_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = RoyalBluePrimary,
                                            strokeWidth = 2.5.dp
                                        )
                                        Column {
                                            Text(
                                                text = "Verifying Payment Screenshot (1-10 sec)...",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = RoyalBlueDeep
                                            )
                                            Text(
                                                text = "Matching ₹${currentAmount.toInt()} payment receipt",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            } else {
                                BlueGradientButton(
                                    text = "Verify Screenshot & Pay ${formatInr(currentAmount)}",
                                    enabled = !isExpired,
                                    onClick = {
                                        val amt = amountText.toDoubleOrNull() ?: 0.0
                                        onSubmitRecharge(amt, selectedMethod, utrReference, screenshotUri)
                                    },
                                    testTag = "recharge_submit_button",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = { isGatewayStep = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel / Change Amount", color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicUpiQrCode(
    @DrawableRes scannerResId: Int,
    scannerNumber: Int,
    amount: Double
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(200.dp)
                .border(1.dp, RoyalSkyBorder, RoundedCornerShape(14.dp))
                .testTag("recharge_dynamic_qr")
        ) {
            Image(
                painter = painterResource(id = scannerResId),
                contentDescription = "UPI QR Scanner $scannerNumber for ${formatInr(amount)}",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Scanner #$scannerNumber • Pay ${formatInr(amount)} via PhonePe / Paytm / GPay",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = RoyalBlueDeep
            )
        }
    }
}

@Composable
fun WithdrawalScreen(
    user: UserEntity?,
    bankCards: List<BankCardEntity>,
    withdrawals: List<WithdrawalEntity>,
    onBack: () -> Unit,
    onOpenBankCards: () -> Unit,
    onSubmitWithdrawal: (
        amount: Double,
        payoutMethod: String,
        upiId: String,
        bankAccount: String,
        holderName: String,
        ifsc: String,
        withdrawPassword: String
    ) -> Unit
) {
    val s = LocalStrings.current
    var amountText by remember { mutableStateOf("") }
    var payoutMethod by remember { mutableStateOf("UPI") }
    var upiId by remember { mutableStateOf(bankCards.firstOrNull()?.upiId ?: "") }
    var bankAccount by remember { mutableStateOf(bankCards.firstOrNull()?.accountNumber ?: "") }
    var holderName by remember { mutableStateOf(bankCards.firstOrNull()?.accountHolderName ?: "") }
    var ifsc by remember { mutableStateOf(bankCards.firstOrNull()?.ifscCode ?: "") }
    var withdrawPassword by remember { mutableStateOf("") }

    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val fee = (parsedAmount * RoyalBulletRepository.WITHDRAWAL_FEE_PERCENT * 100).toInt() / 100.0
    val net = (parsedAmount - fee).coerceAtLeast(0.0)

    Scaffold(
        topBar = { RoyalTopBar(title = s.withdraw, onBack = onBack) },
        containerColor = IceBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RoyalCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Eligible Available Balance", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                Text(
                                    formatInr(user?.availableBalance ?: 0.0),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = RoyalBluePrimary
                                )
                            }
                            OutlinedButton(onClick = onOpenBankCards) {
                                Text("Saved Cards (${bankCards.size})")
                            }
                        }

                        Text(
                            "Promotional (${formatInr(user?.promotionalBalance ?: 0.0)}) & Deposit (${formatInr(user?.depositBalance ?: 0.0)}) balances are not withdrawable.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextMuted
                        )

                        if (bankCards.isNotEmpty()) {
                            Text("Auto-fill from Saved Bank Card:", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                bankCards.take(2).forEach { card ->
                                    FilterChip(
                                        selected = holderName == card.accountHolderName,
                                        onClick = {
                                            holderName = card.accountHolderName
                                            upiId = card.upiId
                                            bankAccount = card.accountNumber
                                            ifsc = card.ifscCode
                                        },
                                        label = { Text(card.accountHolderName) }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = payoutMethod == "UPI",
                                onClick = { payoutMethod = "UPI" },
                                label = { Text("UPI Payout") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBluePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = payoutMethod == "Bank",
                                onClick = { payoutMethod = "Bank" },
                                label = { Text("Bank Transfer (IMPS/NEFT)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBluePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Withdrawal Amount (Min ₹150)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_amount_input")
                        )

                        OutlinedTextField(
                            value = holderName,
                            onValueChange = { holderName = it },
                            label = { Text("Account Holder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (payoutMethod == "UPI") {
                            OutlinedTextField(
                                value = upiId,
                                onValueChange = { upiId = it },
                                label = { Text("UPI ID (e.g. rider@okaxis)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("withdraw_upi_input")
                            )
                        } else {
                            OutlinedTextField(
                                value = bankAccount,
                                onValueChange = { bankAccount = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Bank Account Number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = ifsc,
                                onValueChange = { ifsc = it.uppercase() },
                                label = { Text("IFSC Code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = withdrawPassword,
                            onValueChange = { withdrawPassword = it },
                            label = { Text("Withdrawal Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_password_input")
                        )

                        Surface(
                            color = RoyalSkyLight,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Fee (2%): ${formatInr(fee)} • Estimated Net Payout: ${formatInr(net)}", style = MaterialTheme.typography.labelLarge, color = RoyalBlueDeep)
                                Text("Daily Limit: ₹150 – ₹50,000 • Processing: 24–48 business hours after verification.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = TextSecondary)
                            }
                        }

                        BlueGradientButton(
                            text = "Submit Withdrawal Request",
                            onClick = {
                                onSubmitWithdrawal(
                                    parsedAmount, payoutMethod, upiId, bankAccount,
                                    holderName, ifsc, withdrawPassword
                                )
                                withdrawPassword = ""
                            },
                            testTag = "withdraw_submit_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Text("Withdrawal History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }

            if (withdrawals.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.AccountBalance,
                        title = "No Withdrawal Requests",
                        subtitle = "Eligible verified earnings (min ₹150) can be withdrawn to UPI or Bank Account."
                    )
                }
            } else {
                items(withdrawals, key = { it.id }) { item ->
                    RoyalCard {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${formatInr(item.amount)} (${item.payoutMethod})", style = MaterialTheme.typography.titleMedium)
                                StatusPill(item.status)
                            }
                            Text(
                                "Net: ${formatInr(item.netAmount)} • Fee: ${formatInr(item.feeAmount)} • ${item.accountHolderName}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = TextSecondary
                            )
                            Text(
                                formatTimestamp(item.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BankCardScreen(
    bankCards: List<BankCardEntity>,
    onBack: () -> Unit,
    onSaveCard: (holder: String, bank: String, account: String, ifsc: String, upi: String) -> Unit,
    onDeleteCard: (String) -> Unit
) {
    val s = LocalStrings.current
    var holder by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var upi by remember { mutableStateOf("") }

    Scaffold(
        topBar = { RoyalTopBar(title = s.bankCard, onBack = onBack) },
        containerColor = IceBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RoyalCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Add Bank Account / UPI", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = holder,
                            onValueChange = { holder = it },
                            label = { Text("Account Holder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text("Bank Name (optional for UPI)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = account,
                            onValueChange = { account = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Account Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ifsc,
                            onValueChange = { ifsc = it.uppercase() },
                            label = { Text("IFSC Code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = upi,
                            onValueChange = { upi = it },
                            label = { Text("UPI ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BlueGradientButton(
                            text = "Save Bank / UPI Card",
                            onClick = {
                                onSaveCard(holder, bankName, account, ifsc, upi)
                                holder = ""; bankName = ""; account = ""; ifsc = ""; upi = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            items(bankCards, key = { it.id }) { card ->
                RoyalCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.accountHolderName, style = MaterialTheme.typography.titleMedium)
                            if (card.accountNumber.isNotBlank()) {
                                Text(
                                    "${card.bankName.ifBlank { "Bank" }} • ${card.accountNumber} (${card.ifscCode})",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = TextSecondary
                                )
                            }
                            if (card.upiId.isNotBlank()) {
                                Text("UPI: ${card.upiId}", style = MaterialTheme.typography.labelMedium, color = RoyalBluePrimary)
                            }
                        }
                        IconButton(onClick = { onDeleteCard(card.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = ErrorRed)
                        }
                    }
                }
            }
        }
    }
}
