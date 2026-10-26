package com.barshan.aiphotoeditor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SegmentationMaskDecoderTest {
    @Test
    fun decodesFloatConfidenceValuesRatherThanIndividualBytes() {
        val buffer = ByteBuffer.allocate(3 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        buffer.putFloat(0.0f)
        buffer.putFloat(0.5f)
        buffer.putFloat(1.0f)
        buffer.rewind()

        assertArrayEquals(
            floatArrayOf(0.0f, 0.5f, 1.0f),
            SegmentationMaskDecoder.decode(buffer, 3, 1),
            0.0001f
        )
    }

    @Test
    fun clampsInvalidConfidenceValuesToSafeRange() {
        val buffer = ByteBuffer.allocate(4 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        buffer.putFloat(-0.5f)
        buffer.putFloat(1.5f)
        buffer.putFloat(Float.NaN)
        buffer.putFloat(0.25f)
        buffer.rewind()

        assertArrayEquals(
            floatArrayOf(0f, 1f, 0f, 0.25f),
            SegmentationMaskDecoder.decode(buffer, 2, 2),
            0.0001f
        )
    }

    @Test
    fun rejectsInvalidDimensionsAndShortBuffers() {
        assertThrows(IllegalArgumentException::class.java) {
            SegmentationMaskDecoder.decode(ByteBuffer.allocate(4), 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SegmentationMaskDecoder.decode(ByteBuffer.allocate(1), 1, 1)
        }
    }

    @Test
    fun decodingDoesNotChangeOriginalBufferPosition() {
        val buffer = ByteBuffer.allocate(2 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        buffer.putFloat(0.2f)
        buffer.putFloat(0.8f)
        buffer.position(Float.SIZE_BYTES)

        SegmentationMaskDecoder.decode(buffer, 2, 1)
        assertEquals(Float.SIZE_BYTES, buffer.position())
    }
}
