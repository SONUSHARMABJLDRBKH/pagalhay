package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

data class ScreenshotVerificationResult(
    val verified: Boolean,
    val detectedAmountInr: Double,
    val detectedUtr: String,
    val failureReason: String = ""
)

object PaymentScreenshotVerifier {
    private const val TAG = "PaymentScreenshotVerify"

    private val verificationSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "isPaymentScreenshot" to Schema.boolean(
                    description = "True only if this image is a genuine digital UPI/PhonePe/Paytm/Google Pay/bank payment confirmation screenshot."
                ),
                "isSuccessfulStatus" to Schema.boolean(
                    description = "True if the payment shown is completed/successful; false if pending, failed, cancelled, or unclear."
                ),
                "detectedAmountInr" to Schema.double(
                    description = "The exact transaction amount paid in Indian Rupees (INR), e.g. 700.0 or 1500.0. Return 0.0 if no amount is visible."
                ),
                "detectedUtr" to Schema.string(
                    description = "Any visible UTR / UPI Ref / Transaction ID string, or empty string if none visible."
                ),
                "reason" to Schema.string(
                    description = "Short explanation of why the screenshot passed or failed verification."
                )
            ),
            optionalProperties = listOf("detectedUtr")
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = verificationSchema
            }
        )
    }

    suspend fun verifyPaymentScreenshot(
        context: Context,
        imageUri: Uri,
        expectedAmountInr: Double,
        enteredUtr: String
    ): ScreenshotVerificationResult {
        val startMs = System.currentTimeMillis()
        val bitmap = withContext(Dispatchers.IO) {
            decodeScaledBitmap(context, imageUri, maxDimension = 896)
        } ?: return ScreenshotVerificationResult(
            verified = false,
            detectedAmountInr = 0.0,
            detectedUtr = "",
            failureReason = "Unable to read the selected screenshot from gallery. Please pick a valid image."
        )

        return try {
            val response = withTimeout(10_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Verify this uploaded image for a wallet recharge of ₹${expectedAmountInr.toInt()} " +
                                "with user-entered UTR '$enteredUtr'. " +
                                "Check strictly:\n" +
                                "1) Is this actually a UPI / PhonePe / Paytm / Google Pay / bank payment screenshot?\n" +
                                "2) Does it show a completed/successful payment (not failed/cancelled/pending)?\n" +
                                "3) What exact INR amount is shown paid?\n" +
                                "4) Extract any visible UTR / Ref ID."
                        )
                    }
                )
            }

            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return ScreenshotVerificationResult(
                    verified = false,
                    detectedAmountInr = 0.0,
                    detectedUtr = "",
                    failureReason = "Payment verification returned an empty response. Payment failed."
                )
            }

            val json = JSONObject(rawJson)
            val isPaymentScreenshot = json.optBoolean("isPaymentScreenshot", false)
            val isSuccessfulStatus = json.optBoolean("isSuccessfulStatus", false)
            val detectedAmount = json.optDouble("detectedAmountInr", 0.0)
            val detectedUtr = json.optString("detectedUtr", "").trim()
            val modelReason = json.optString("reason", "").trim()

            when {
                !isPaymentScreenshot -> ScreenshotVerificationResult(
                    verified = false,
                    detectedAmountInr = detectedAmount,
                    detectedUtr = detectedUtr,
                    failureReason = modelReason.ifBlank {
                        "Payment Failed: Uploaded image is not a valid UPI/payment screenshot."
                    }
                )
                !isSuccessfulStatus -> ScreenshotVerificationResult(
                    verified = false,
                    detectedAmountInr = detectedAmount,
                    detectedUtr = detectedUtr,
                    failureReason = modelReason.ifBlank {
                        "Payment Failed: Screenshot does not show a Successful payment."
                    }
                )
                detectedAmount <= 0.0 || abs(detectedAmount - expectedAmountInr) > 0.5 -> ScreenshotVerificationResult(
                    verified = false,
                    detectedAmountInr = detectedAmount,
                    detectedUtr = detectedUtr,
                    failureReason = "Payment Failed: Screenshot amount (₹${detectedAmount.toInt()}) does not match recharge amount (₹${expectedAmountInr.toInt()})."
                )
                else -> {
                    val elapsed = System.currentTimeMillis() - startMs
                    if (elapsed < 1_200L) {
                        delay(1_200L - elapsed)
                    }
                    ScreenshotVerificationResult(
                        verified = true,
                        detectedAmountInr = detectedAmount,
                        detectedUtr = detectedUtr
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Screenshot verification timed out", e)
            ScreenshotVerificationResult(
                verified = false,
                detectedAmountInr = 0.0,
                detectedUtr = "",
                failureReason = "Verification timed out. Please check your internet connection and retry."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot verification error", e)
            ScreenshotVerificationResult(
                verified = false,
                detectedAmountInr = 0.0,
                detectedUtr = "",
                failureReason = "Verification failed: ${e.localizedMessage ?: "Unable to verify payment screenshot."}"
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return@runCatching null

            var sampleSize = 1
            val largest = max(srcW, srcH)
            while (largest / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }.getOrNull()
    }
}
