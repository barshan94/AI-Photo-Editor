package com.barshan.aiphotoeditor

import java.nio.ByteBuffer

/** Converts ML Kit's float-confidence mask buffer into bounded per-pixel confidence values. */
object SegmentationMaskDecoder {
    fun decode(buffer: ByteBuffer, width: Int, height: Int): FloatArray {
        require(width > 0 && height > 0) { "Mask dimensions must be positive" }
        val count = Math.multiplyExact(width, height)
        require(buffer.capacity() >= Math.multiplyExact(count, Float.SIZE_BYTES)) {
            "Mask buffer is smaller than the expected float-confidence data"
        }

        val input = buffer.duplicate()
        input.rewind()
        return FloatArray(count) {
            val confidence = input.getFloat()
            if (confidence.isNaN()) 0f else confidence.coerceIn(0f, 1f)
        }
    }
}
