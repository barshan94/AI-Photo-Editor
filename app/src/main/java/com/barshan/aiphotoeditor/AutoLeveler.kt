package com.barshan.aiphotoeditor

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Estimates a small camera roll from strong near-horizontal image edges.
 * This is a local, heuristic horizon/line estimator; it returns null when the
 * photo has too little reliable horizontal structure rather than guessing.
 */
object AutoLeveler {
    fun estimateCorrectionDegrees(bitmap: Bitmap): Float? {
        if (bitmap.width < 32 || bitmap.height < 32) return null
        val sampleScale = minOf(1f, 480f / maxOf(bitmap.width, bitmap.height).toFloat())
        val width = (bitmap.width * sampleScale).toInt().coerceAtLeast(32)
        val height = (bitmap.height * sampleScale).toInt().coerceAtLeast(32)
        val sample = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val pixels = IntArray(width * height)
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
            val angles = ArrayList<Pair<Float, Float>>()
            var totalWeight = 0.0
            val step = 2
            for (y in 1 until height - 1 step step) {
                for (x in 1 until width - 1 step step) {
                    fun lum(i: Int): Float {
                        val p = pixels[i]
                        return (0.213f * (p shr 16 and 255) + 0.715f * (p shr 8 and 255) + 0.072f * (p and 255))
                    }
                    val gx = -lum((y - 1) * width + x - 1) + lum((y - 1) * width + x + 1) -
                        2f * lum(y * width + x - 1) + 2f * lum(y * width + x + 1) -
                        lum((y + 1) * width + x - 1) + lum((y + 1) * width + x + 1)
                    val gy = -lum((y - 1) * width + x - 1) - 2f * lum((y - 1) * width + x) -
                        lum((y - 1) * width + x + 1) + lum((y + 1) * width + x - 1) +
                        2f * lum((y + 1) * width + x) + lum((y + 1) * width + x + 1)
                    val magnitude = sqrt(gx * gx + gy * gy)
                    if (magnitude < 70f) continue
                    var edgeAngle = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat() + 90f
                    while (edgeAngle > 90f) edgeAngle -= 180f
                    while (edgeAngle < -90f) edgeAngle += 180f
                    if (abs(edgeAngle) <= 12f) {
                        val weight = (magnitude - 70f).coerceAtMost(255f).toDouble()
                        angles.add(edgeAngle to weight.toFloat())
                        totalWeight += weight
                    }
                }
            }
            if (angles.size < 30 || totalWeight < 2500.0) return null
            angles.sortBy { it.first }
            var running = 0.0
            for ((angle, weight) in angles) {
                running += weight
                if (running >= totalWeight / 2.0) {
                    // The detected edge leans by angle; rotate in the opposite direction.
                    val correction = -angle
                    return if (abs(correction) < 0.6f) 0f else correction.coerceIn(-12f, 12f)
                }
            }
            return null
        } finally {
            if (sample !== bitmap && !sample.isRecycled) sample.recycle()
        }
    }
}