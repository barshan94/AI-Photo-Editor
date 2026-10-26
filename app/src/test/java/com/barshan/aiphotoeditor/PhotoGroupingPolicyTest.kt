package com.barshan.aiphotoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGroupingPolicyTest {
    @Test
    fun groupsFramesWithinTwoSecondsOfFirstCapture() {
        assertEquals(listOf(1_000L, 1_000L, null),
            PhotoGroupingPolicy.burstGroupIds(listOf(1_000L, 2_500L, 3_100L)))
    }

    @Test
    fun preventsLongAdjacentTimestampChainsFromBecomingOneBurst() {
        assertEquals(listOf(0L, 0L, null),
            PhotoGroupingPolicy.burstGroupIds(listOf(0L, 1_500L, 3_000L)))
    }

    @Test
    fun missingCaptureTimesStayUngroupedAndOriginalOrderIsPreserved() {
        assertEquals(listOf(5_000L, null, 5_000L),
            PhotoGroupingPolicy.burstGroupIds(listOf(5_500L, null, 5_000L)))
    }

    @Test
    fun perceptualHashThresholdIsAppliedConsistently() {
        assertTrue(PhotoGroupingPolicy.isVisuallySimilar(0L, 31L, maxBitDistance = 5))
        assertFalse(PhotoGroupingPolicy.isVisuallySimilar(0L, 63L, maxBitDistance = 5))
    }
}
