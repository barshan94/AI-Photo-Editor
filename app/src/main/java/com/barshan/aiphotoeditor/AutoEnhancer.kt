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

/** Non-destructive, on-device auto enhancement. Original files are never edited. */
object AutoEnhancer {
    fun decodeForEditing(resolver: ContentResolver, uri: Uri, maxDimension: Int = 1800): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open this photo")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
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

        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED) return decoded

        val matrix = Matrix().apply {
            setValues(ExifOrientationTransform.matrixValues(orientation, decoded.width, decoded.height))
        }
        val oriented = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (error: RuntimeException) {
            if (!decoded.isRecycled) decoded.recycle()
            throw IllegalArgumentException("Could not apply this photo's orientation", error)
        }
        if (oriented !== decoded && !decoded.isRecycled) decoded.recycle()
        return oriented
    }

    /**
     * Applies a restrained exposure correction, contrast lift and saturation lift.
     * Input bitmap is left unchanged; caller owns both bitmaps.
     */
    fun enhance(source: Bitmap, settings: EditSettings = EditSettings()): Bitmap {
        val width = source.width
        val height = source.height
        val sampleStride = ((width * height) / 20000).coerceAtLeast(1)
        val row = IntArray(width)

        // First pass: estimate brightness without allocating an image-sized IntArray.
        var lumaSum = 0.0
        var sampleCount = 0
        var globalIndex = 0
        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if (globalIndex % sampleStride == 0) {
                    val pixel = row[x]
                    lumaSum += 0.2126 * (pixel shr 16 and 0xFF) +
                        0.7152 * (pixel shr 8 and 0xFF) +
                        0.0722 * (pixel and 0xFF)
                    sampleCount++
                }
                globalIndex++
            }
        }
        val meanLuma = if (sampleCount == 0) 128.0 else lumaSum / sampleCount
        val autoBrightness = ((128.0 - meanLuma) * 0.20).coerceIn(-10.0, 10.0)
        val brightnessOffset = autoBrightness + settings.brightness
        val contrast = settings.contrast.toDouble().coerceIn(0.65, 1.45)
        val saturation = settings.saturation.toDouble().coerceIn(0.0, 1.8)
        val warmth = settings.warmth.toDouble().coerceIn(-24.0, 24.0)

        // Second pass: transform one row at a time to keep peak memory lower on phones.
        val adjusted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = row[x]
                val alpha = pixel ushr 24 and 0xFF
                val originalRed = pixel shr 16 and 0xFF
                val originalGreen = pixel shr 8 and 0xFF
                val originalBlue = pixel and 0xFF

                val red = ((originalRed - 128.0) * contrast + 128.0 + brightnessOffset + warmth)
                val green = ((originalGreen - 128.0) * contrast + 128.0 + brightnessOffset)
                val blue = ((originalBlue - 128.0) * contrast + 128.0 + brightnessOffset - warmth)
                val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue

                val outRed = (luma + (red - luma) * saturation).toInt().coerceIn(0, 255)
                val outGreen = (luma + (green - luma) * saturation).toInt().coerceIn(0, 255)
                val outBlue = (luma + (blue - luma) * saturation).toInt().coerceIn(0, 255)
                row[x] = (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
            }
            adjusted.setPixels(row, 0, width, 0, y, width, 1)
        }

        val transform = Matrix().apply {
            postScale(if (settings.mirrorHorizontal) -1f else 1f, if (settings.mirrorVertical) -1f else 1f)
            postRotate(settings.rotationDegrees + settings.straightenDegrees)
        }
        val transformed = if (settings.mirrorHorizontal || settings.mirrorVertical || settings.rotationDegrees % 360f != 0f || settings.straightenDegrees != 0f) {
            Bitmap.createBitmap(adjusted, 0, 0, adjusted.width, adjusted.height, transform, true).also { if (it !== adjusted) adjusted.recycle() }
        } else adjusted
        val ratio = settings.cropRatio ?: return transformed
        if (ratio <= 0f) return transformed
        val sourceRatio = transformed.width.toFloat() / transformed.height.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (sourceRatio > ratio) {
            cropHeight = transformed.height
            cropWidth = (cropHeight * ratio).toInt().coerceIn(1, transformed.width)
        } else {
            cropWidth = transformed.width
            cropHeight = (cropWidth / ratio).toInt().coerceIn(1, transformed.height)
        }
        val availableX = (transformed.width - cropWidth).coerceAtLeast(0)
        val availableY = (transformed.height - cropHeight).coerceAtLeast(0)
        val left = ((availableX * ((settings.cropOffsetX.coerceIn(-1f, 1f) + 1f) / 2f)).toInt())
            .coerceIn(0, availableX)
        val top = ((availableY * ((settings.cropOffsetY.coerceIn(-1f, 1f) + 1f) / 2f)).toInt())
            .coerceIn(0, availableY)
        val cropped = Bitmap.createBitmap(transformed, left, top, cropWidth, cropHeight)
        if (cropped !== transformed) transformed.recycle()
        return cropped
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
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) {
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
