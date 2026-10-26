package com.barshan.aiphotoeditor

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** Deterministic technical scoring kept pure for regression tests. */
object PhotoQualityScoring {
    data class Score(
        val score: Int,
        val sharpness: Int,
        val exposure: Int,
        val contrast: Int,
        val note: String
    )

    fun scorePixels(pixels: IntArray, width: Int, height: Int): Score {
        if (width < 3 || height < 3 || pixels.size < width * height) {
            return Score(0, 0, 0, 0, "Image is too small to analyse")
        }

        var luminanceSum = 0.0
        var luminanceSquaredSum = 0.0
        var clipped = 0
        for (pixel in pixels) {
            val luma = luminance(pixel)
            luminanceSum += luma
            luminanceSquaredSum += luma * luma
            if (luma < 7.0 || luma > 248.0) clipped++
        }

        val count = width * height
        val mean = luminanceSum / count
        val variance = (luminanceSquaredSum / count - mean * mean).coerceAtLeast(0.0)
        val standardDeviation = sqrt(variance)
        val clippingRatio = clipped.toDouble() / count
        val exposureBase = (100.0 - abs(mean - 128.0) * 0.72).coerceIn(0.0, 100.0)
        val exposureScore = (exposureBase - clippingRatio * 110.0).coerceIn(0.0, 100.0)
        val contrastScore = (standardDeviation / 58.0 * 100.0).coerceIn(0.0, 100.0)

        var edgeEnergy = 0.0
        var edgeCount = 0
        var y = 1
        while (y < height - 1) {
            var x = 1
            while (x < width - 1) {
                val i = y * width + x
                val laplacian = 4.0 * luminance(pixels[i]) -
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
        return Score(total, sharpnessScore.toInt(), exposureScore.toInt(), contrastScore.toInt(), note)
    }

    private fun luminance(pixel: Int): Double =
        0.2126 * (pixel shr 16 and 0xFF) +
            0.7152 * (pixel shr 8 and 0xFF) +
            0.0722 * (pixel and 0xFF)
}
