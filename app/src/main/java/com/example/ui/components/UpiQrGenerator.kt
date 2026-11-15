package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.core.content.FileProvider
import com.example.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object UpiQrGenerator {
    private const val PREFS_NAME = "royal_bullet_qr_prefs"
    private const val KEY_SCANNER_INDEX = "last_qr_4way_index"

    val scannerDrawables: List<Int> = listOf(
        R.drawable.qr_code_1,
        R.drawable.qr_code_2,
        R.drawable.qr_code_3,
        R.drawable.qr_code_4
    )

    fun nextScannerIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prev = prefs.getInt(KEY_SCANNER_INDEX, -1)
        val next = (prev + 1) % scannerDrawables.size
        prefs.edit().putInt(KEY_SCANNER_INDEX, next).apply()
        return next
    }

    @DrawableRes
    fun scannerDrawableForIndex(index: Int): Int {
        val safeIdx = ((index % scannerDrawables.size) + scannerDrawables.size) % scannerDrawables.size
        return scannerDrawables[safeIdx]
    }

    fun buildUpiPaymentUri(
        merchantUpiId: String,
        merchantName: String = "Royal Bullet 350",
        amount: Double
    ): String {
        val cleanUpi = merchantUpiId.trim().ifBlank { "7544985606@ybl" }
        val formattedAmount = String.format(Locale.US, "%.2f", amount.coerceAtLeast(1.0))
        return "upi://pay?pa=$cleanUpi" +
            "&pn=${Uri.encode(merchantName)}" +
            "&am=$formattedAmount" +
            "&cu=INR"
    }

    fun decodeUpiUriFromDrawable(context: Context, @DrawableRes drawableResId: Int): String? {
        return runCatching {
            val bitmap = BitmapFactory.decodeResource(context.resources, drawableResId) ?: return null
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf(DecodeHintType.TRY_HARDER to true)
            val result = MultiFormatReader().decode(binaryBitmap, hints)
            val text = result.text?.trim()
            if (!text.isNullOrBlank() && text.startsWith("upi://", ignoreCase = true)) {
                text
            } else null
        }.getOrNull()
    }

    fun resolvePaymentUriForScanner(
        context: Context,
        @DrawableRes drawableResId: Int,
        fallbackMerchantUpiId: String,
        amount: Double
    ): String {
        val decoded = decodeUpiUriFromDrawable(context, drawableResId)
        if (decoded.isNullOrBlank()) {
            return buildUpiPaymentUri(merchantUpiId = fallbackMerchantUpiId, amount = amount)
        }
        val formattedAmount = String.format(Locale.US, "%.2f", amount.coerceAtLeast(1.0))
        val hasSignature = decoded.contains("sign=", ignoreCase = true)
        if (hasSignature) {
            return decoded
        }
        val parsed = Uri.parse(decoded)
        val hasAmount = !parsed.getQueryParameter("am").isNullOrBlank()
        return if (!hasAmount) {
            val sep = if (decoded.contains("?")) "&" else "?"
            "${decoded}${sep}am=$formattedAmount"
        } else {
            decoded.replace(Regex("([?&])am=[^&]*", RegexOption.IGNORE_CASE), "$1am=$formattedAmount")
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 768): Bitmap? {
        return runCatching {
            val hints = mapOf(
                EncodeHintType.MARGIN to 3,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    fun generateShareableScannerCardBitmap(
        context: Context,
        @DrawableRes drawableResId: Int,
        amount: Double
    ): Bitmap? {
        val qrBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, drawableResId)
        }.getOrNull() ?: return null
        return runCatching {
            val cardWidth = 800
            val cardHeight = 920
            val card = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(card)
            canvas.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A3D91.toInt()
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF475569.toInt()
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText("Royal Bullet 350", cardWidth / 2f, 74f, titlePaint)
            canvas.drawText("Pay ₹${amount.toInt()}", cardWidth / 2f, 140f, amountPaint)
            val qrLeft = 80
            val qrTop = 175
            val qrSize = 640
            canvas.drawBitmap(
                qrBitmap,
                null,
                Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
                null
            )
            canvas.drawText("Scan with PhonePe • Paytm • Google Pay", cardWidth / 2f, 865f, footerPaint)
            card
        }.getOrNull()
    }

    fun saveScannerQrToGallery(
        context: Context,
        @DrawableRes drawableResId: Int,
        amount: Double
    ): Boolean {
        val bitmap = generateShareableScannerCardBitmap(context, drawableResId, amount) ?: return false
        return runCatching {
            val fileName = "RoyalBullet_QR_${amount.toInt()}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RoyalBullet")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun shareScannerQrImage(
        context: Context,
        @DrawableRes drawableResId: Int,
        amount: Double,
        targetPackage: String? = null
    ): Boolean {
        val bitmap = generateShareableScannerCardBitmap(context, drawableResId, amount) ?: return false
        return runCatching {
            val cacheDir = File(context.cacheDir, "qr").apply { mkdirs() }
            val outFile = File(cacheDir, "upi_qr_${amount.toInt()}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!targetPackage.isNullOrBlank()) {
                    setPackage(targetPackage)
                }
            }
            context.startActivity(
                if (!targetPackage.isNullOrBlank()) sendIntent
                else Intent.createChooser(sendIntent, "Send QR to PhonePe / Paytm / GPay")
            )
            true
        }.getOrDefault(false)
    }
}
