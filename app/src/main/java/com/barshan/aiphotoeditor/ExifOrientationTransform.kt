package com.barshan.aiphotoeditor

/**
 * EXIF orientation transform represented as Android Matrix's 3x3 row-major values.
 * The returned transform covers all eight EXIF orientations, including mirrored images.
 */
object ExifOrientationTransform {
    fun matrixValues(orientation: Int, width: Int, height: Int): FloatArray {
        val w = width.toFloat()
        val h = height.toFloat()
        return when (orientation) {
            2 -> floatArrayOf(-1f, 0f, w, 0f, 1f, 0f, 0f, 0f, 1f) // mirror horizontal
            3 -> floatArrayOf(-1f, 0f, w, 0f, -1f, h, 0f, 0f, 1f) // rotate 180
            4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, h, 0f, 0f, 1f) // mirror vertical
            5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) // transpose
            6 -> floatArrayOf(0f, -1f, h, 1f, 0f, 0f, 0f, 0f, 1f) // rotate 90
            7 -> floatArrayOf(0f, -1f, h, -1f, 0f, w, 0f, 0f, 1f) // transverse
            8 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, w, 0f, 0f, 1f) // rotate 270
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }
    }

    fun swapsDimensions(orientation: Int): Boolean = orientation in 5..8
}
