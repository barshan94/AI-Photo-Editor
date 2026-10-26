package com.barshan.aiphotoeditor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
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
            if (width < 3 || height < 3) {
                return Result(0, 0, 0, 0, "Image is too small to analyse")
            }

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            var luminanceSum = 0.0
            var luminanceSquaredSum = 0.0
            var count = 0
            var clipped = 0

            for (pixel in pixels) {
                val luma = luminance(pixel)
                luminanceSum += luma
                luminanceSquaredSum += luma * luma
                if (luma < 7.0 || luma > 248.0) clipped++
                count++
            }

            val mean = luminanceSum / count
            val variance = (luminanceSquaredSum / count - mean * mean).coerceAtLeast(0.0)
            val standardDeviation = sqrt(variance)
            val clippingRatio = clipped.toDouble() / count

            // Penalise very dark/bright images and excessive crushed/blown pixels.
            val exposureBase = (100.0 - abs(mean - 128.0) * 0.72).coerceIn(0.0, 100.0)
            val exposureScore = (exposureBase - clippingRatio * 110.0).coerceIn(0.0, 100.0)

            // Contrast is estimated from luminance spread.
            val contrastScore = (standardDeviation / 58.0 * 100.0).coerceIn(0.0, 100.0)

            // Laplacian energy is a lightweight sharpness proxy. It cannot reliably
            // distinguish intentional background blur from an out-of-focus subject.
            var edgeEnergy = 0.0
            var edgeCount = 0
            var y = 1
            while (y < height - 1) {
                var x = 1
                while (x < width - 1) {
                    val i = y * width + x
                    val center = luminance(pixels[i])
                    val laplacian = 4.0 * center -
                        luminance(pixels[i - 1]) -
                        luminance(pixels[i + 1]) -
                        luminance(pixels[i - width]) -
                        luminance(pixels[i + width])
                    edgeEnergy += laplacian * laplacian
                    edgeCount++
                    x += 2
                }
                y += 2
            }
            val laplacianVariance = if (edgeCount == 0) 0.0 else edgeEnergy / edgeCount
            val sharpnessScore = (ln(laplacianVariance + 1.0) / ln(1201.0) * 100.0)
                .coerceIn(0.0, 100.0)

            val total = (sharpnessScore * 0.45 + exposureScore * 0.30 + contrastScore * 0.25)
                .toInt().coerceIn(0, 100)
            val weakest = listOf(
                "Sharpness" to sharpnessScore,
                "Exposure" to exposureScore,
                "Contrast" to contrastScore
            ).minBy { it.second }.first
            val note = when {
                clippingRatio > 0.18 -> "Many very dark or bright areas"
                weakest == "Sharpness" -> "Check for motion or focus blur"
                weakest == "Exposure" -> "Lighting may need adjustment"
                weakest == "Contrast" -> "Low tonal contrast"
                else -> "Good technical balance"
            }

            return Result(
                score = total,
                sharpness = sharpnessScore.toInt(),
                exposure = exposureScore.toInt(),
                contrast = contrastScore.toInt(),
                note = note
            )
        } finally {
            bitmap.recycle()
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

    private fun luminance(pixel: Int): Double {
        val red = (pixel shr 16 and 0xFF).toDouble()
        val green = (pixel shr 8 and 0xFF).toDouble()
        val blue = (pixel and 0xFF).toDouble()
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }
}
