package com.barshan.aiphotoeditor

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Estimates camera roll from the dominant cluster of near-horizontal edges.
 * A dominant-angle histogram is more robust than taking the median of every
 * edge in a photo, where vertical objects and texture can overwhelm the horizon.
 */
object AutoLeveler {
    private const val MAX_CORRECTION = 12f
    private const val BIN_STEP = 0.5f
    private const val MIN_EDGE_MAGNITUDE = 55f

    fun estimateCorrectionDegrees(bitmap: Bitmap): Float? {
        if (bitmap.width < 32 || bitmap.height < 32) return null
        val scale = minOf(1f, 640f / maxOf(bitmap.width, bitmap.height).toFloat())
        val width = (bitmap.width * scale).toInt().coerceAtLeast(32)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(32)
        val sample = if (width == bitmap.width && height == bitmap.height) bitmap
            else Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val pixels = IntArray(width * height)
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
            val binCount = (MAX_CORRECTION * 2 / BIN_STEP).toInt() + 1
            val histogram = DoubleArray(binCount)
            val angleSamples = ArrayList<Pair<Float, Float>>()
            fun lum(i: Int): Float {
                val p = pixels[i]
                return 0.213f * (p shr 16 and 255) + 0.715f * (p shr 8 and 255) + 0.072f * (p and 255)
            }
            var totalWeight = 0.0
            for (y in 1 until height - 1 step 2) {
                for (x in 1 until width - 1 step 2) {
                    val tl = lum((y - 1) * width + x - 1)
                    val tc = lum((y - 1) * width + x)
                    val tr = lum((y - 1) * width + x + 1)
                    val ml = lum(y * width + x - 1)
                    val mr = lum(y * width + x + 1)
                    val bl = lum((y + 1) * width + x - 1)
                    val bc = lum((y + 1) * width + x)
                    val br = lum((y + 1) * width + x + 1)
                    val gx = -tl + tr - 2f * ml + 2f * mr - bl + br
                    val gy = -tl - 2f * tc - tr + bl + 2f * bc + br
                    val magnitude = sqrt(gx * gx + gy * gy)
                    if (magnitude < MIN_EDGE_MAGNITUDE) continue
                    var angle = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat() + 90f
                    while (angle > 90f) angle -= 180f
                    while (angle < -90f) angle += 180f
                    if (abs(angle) > MAX_CORRECTION) continue
                    val weight = (magnitude - MIN_EDGE_MAGNITUDE).coerceAtMost(255f)
                    val bin = ((angle + MAX_CORRECTION) / BIN_STEP).toInt().coerceIn(0, binCount - 1)
                    histogram[bin] += weight
                    angleSamples.add(angle to weight)
                    totalWeight += weight
                }
            }
            if (angleSamples.size < 24 || totalWeight < 1800.0) return null

            var peak = 0
            var peakWeight = 0.0
            for (i in histogram.indices) {
                val score = histogram.getOrElse(i - 1) { 0.0 } +
                    histogram[i] * 2.0 + histogram.getOrElse(i + 1) { 0.0 }
                if (score > peakWeight) { peakWeight = score; peak = i }
            }
            val dominantAngle = peak * BIN_STEP - MAX_CORRECTION
            val cluster = angleSamples.filter { abs(it.first - dominantAngle) <= 2.0f }
            val clusterWeight = cluster.sumOf { it.second.toDouble() }
            if (cluster.size < 18 || clusterWeight < totalWeight * 0.12) return null
            val sorted = cluster.sortedBy { it.first }
            var running = 0.0
            for ((angle, weight) in sorted) {
                running += weight
                if (running >= clusterWeight / 2.0) {
                    val correction = -angle
                    return if (abs(correction) < 0.35f) 0f else correction.coerceIn(-MAX_CORRECTION, MAX_CORRECTION)
                }
            }
            return null
        } finally {
            if (sample !== bitmap && !sample.isRecycled) sample.recycle()
        }
    }
}
