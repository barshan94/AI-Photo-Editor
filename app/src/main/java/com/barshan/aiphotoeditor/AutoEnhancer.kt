package com.barshan.aiphotoeditor

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Non-destructive, on-device auto enhancement. Original files are never edited. */
object AutoEnhancer {
    fun decodeForEditing(resolver: ContentResolver, uri: Uri, maxDimension: Int = 1800): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("Could not open this photo")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("This image format could not be read")
        }

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Could not decode this photo")

        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            ) } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    /**
     * Applies a restrained exposure correction, contrast lift and saturation lift.
     * Input bitmap is left unchanged; caller owns both bitmaps.
     */
    fun enhance(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val sampleStride = ((width * height) / 20000).coerceAtLeast(1)
        val samplePixels = IntArray((width * height + sampleStride - 1) / sampleStride)
        val allPixels = IntArray(width * height)
        source.getPixels(allPixels, 0, width, 0, 0, width, height)

        var lumaSum = 0.0
        var sampleCount = 0
        var i = 0
        while (i < allPixels.size) {
            val p = allPixels[i]
            lumaSum += 0.2126 * (p shr 16 and 0xFF) +
                0.7152 * (p shr 8 and 0xFF) +
                0.0722 * (p and 0xFF)
            sampleCount++
            i += sampleStride
        }
        val meanLuma = if (sampleCount == 0) 128.0 else lumaSum / sampleCount
        val brightnessOffset = ((128.0 - meanLuma) * 0.20).coerceIn(-10.0, 10.0)
        val contrast = 1.07
        val saturation = 1.06

        for (index in allPixels.indices) {
            val pixel = allPixels[index]
            val alpha = pixel ushr 24 and 0xFF
            val originalRed = pixel shr 16 and 0xFF
            val originalGreen = pixel shr 8 and 0xFF
            val originalBlue = pixel and 0xFF

            val red = ((originalRed - 128.0) * contrast + 128.0 + brightnessOffset)
            val green = ((originalGreen - 128.0) * contrast + 128.0 + brightnessOffset)
            val blue = ((originalBlue - 128.0) * contrast + 128.0 + brightnessOffset)
            val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue

            val outRed = (luma + (red - luma) * saturation).toInt().coerceIn(0, 255)
            val outGreen = (luma + (green - luma) * saturation).toInt().coerceIn(0, 255)
            val outBlue = (luma + (blue - luma) * saturation).toInt().coerceIn(0, 255)
            allPixels[index] = (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(allPixels, 0, width, 0, 0, width, height)
        return output
    }

    /** Save a new JPEG in Pictures/FRAME AI using MediaStore. Returns the saved URI. */
    fun saveJpeg(resolver: ContentResolver, bitmap: Bitmap): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FRAME_AI_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FRAME AI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA,
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .absolutePath + "/FRAME_AI_$stamp.jpg")
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android could not create the exported image")
        try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open the export destination")
            stream.use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) {
                    throw IllegalStateException("JPEG export failed")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, published, null, null)
            }
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
