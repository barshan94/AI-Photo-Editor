package com.barshan.aiphotoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationTransformTest {
    @Test
    fun normalOrientationIsIdentity() {
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            ExifOrientationTransform.matrixValues(1, 400, 300),
            0f
        )
    }

    @Test
    fun allOrientationsReturnValidThreeByThreeMatrices() {
        for (orientation in 1..8) {
            assertEquals(9, ExifOrientationTransform.matrixValues(orientation, 400, 300).size)
        }
    }

    @Test
    fun mirroredOrientationsAreNotMistakenForNormal() {
        assertFalse(ExifOrientationTransform.matrixValues(2, 400, 300)
            .contentEquals(ExifOrientationTransform.matrixValues(1, 400, 300)))
        assertFalse(ExifOrientationTransform.matrixValues(4, 400, 300)
            .contentEquals(ExifOrientationTransform.matrixValues(1, 400, 300)))
    }

    @Test
    fun rotatedAndTransposedOrientationsSwapDimensions() {
        for (orientation in 5..8) assertTrue(ExifOrientationTransform.swapsDimensions(orientation))
        for (orientation in 1..4) assertFalse(ExifOrientationTransform.swapsDimensions(orientation))
    }
}
