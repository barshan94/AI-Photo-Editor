package com.barshan.aiphotoeditor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.security.MessageDigest

/**
 * Fast, on-device technical photo scoring. This is a heuristic, not a judgement
 * of the subject or emotional value of a photo.
 */
object PhotoQualityAnalyzer {
    data class Result(
        val score: Int,
        val sharpness: Int,
        val exposure: Int,
        val contrast: Int,
        val note: String
    )

    fun analyze(resolver: ContentResolver, uri: Uri): Result {
        val bitmap = decodeSmall(resolver, uri)
            ?: throw IllegalArgumentException("Could not read this photo")
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val result = PhotoQualityScoring.scorePixels(pixels, width, height)
            return Result(
                score = result.score,
                sharpness = result.sharpness,
                exposure = result.exposure,
                contrast = result.contrast,
                note = result.note
            )
        } finally {
            bitmap.recycle()
        }
    }


    /**
     * Reads the photo's capture timestamp from MediaStore when available, then EXIF.
     * Returns null when the provider/file has no trustworthy capture-time metadata.
     */
    fun captureTimeMillis(resolver: ContentResolver, uri: Uri): Long? {
        try {
            resolver.query(
                uri,
                arrayOf(MediaStore.Images.ImageColumns.DATE_TAKEN),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    val timestamp = cursor.getLong(0)
                    if (timestamp > 0L) return timestamp
                }
            }
        } catch (_: Exception) {
            // SAF providers do not always expose MediaStore columns.
        }

        return try {
            val input = resolver.openInputStream(uri) ?: return null
            input.use { stream ->
                val exif = ExifInterface(stream)
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: return null
                val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
                val pattern = if (!offset.isNullOrBlank()) {
                    "yyyy:MM:dd HH:mm:ssXXX"
                } else {
                    "yyyy:MM:dd HH:mm:ss"
                }
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                parser.parse(if (!offset.isNullOrBlank()) date + offset else date)?.time
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Makes a compact visual hash from a tiny grayscale image. Similar-looking
     * frames tend to have nearby hashes even when JPEG bytes differ.
     */
    fun visualFingerprint(resolver: ContentResolver, uri: Uri): Long {
        val bitmap = decodeSmall(resolver, uri)
            ?: throw IllegalArgumentException("Could not read photo for visual comparison")
        try {
            val tiny = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            try {
                val pixels = IntArray(64)
                tiny.getPixels(pixels, 0, 8, 0, 0, 8, 8)
                val luminances = pixels.map { pixel ->
                    0.2126 * (pixel shr 16 and 0xFF) +
                        0.7152 * (pixel shr 8 and 0xFF) +
                        0.0722 * (pixel and 0xFF)
                }
                val mean = luminances.average()
                var hash = 0L
                for (luma in luminances) {
                    hash = (hash shl 1) or (if (luma >= mean) 1L else 0L)
                }
                return hash
            } finally {
                if (tiny !== bitmap && !tiny.isRecycled) tiny.recycle()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Hashes the original file stream without loading the entire photo into memory. */
    fun fingerprint(resolver: ContentResolver, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not read photo bytes")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun decodeSmall(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 360 || bounds.outHeight / sample > 360) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }


}
