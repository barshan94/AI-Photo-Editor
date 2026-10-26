package com.barshan.aiphotoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoQualityScoringTest {
    @Test
    fun uniformMidGrayScoresExposureWellButDoesNotInventSharpness() {
        val pixels = IntArray(32 * 32) { 0xFF808080.toInt() }
        val result = PhotoQualityScoring.scorePixels(pixels, 32, 32)
        assertEquals(0, result.sharpness)
        assertEquals(100, result.exposure)
        assertEquals(0, result.contrast)
        assertEquals(30, result.score)
    }

    @Test
    fun clippedDarkImageReceivesLowExposureScore() {
        val result = PhotoQualityScoring.scorePixels(IntArray(32 * 32) { 0xFF000000.toInt() }, 32, 32)
        assertEquals(0, result.exposure)
        assertEquals(0, result.score)
        assertTrue(result.note.contains("dark or bright"))
    }

    @Test
    fun highFrequencyDetailsScoreSharperThanUniformPixels() {
        val smooth = PhotoQualityScoring.scorePixels(IntArray(32 * 32) { 0xFF808080.toInt() }, 32, 32)
        val checker = PhotoQualityScoring.scorePixels(IntArray(32 * 32) { i ->
            if ((i % 32 + i / 32) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }, 32, 32)
        assertTrue(checker.sharpness > smooth.sharpness)
    }

    @Test
    fun scoresStayWithinDocumentedBoundsForDifferentPixelValues() {
        val pixels = IntArray(64 * 64) { i ->
            val value = (i * 37) and 0xFF
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        val result = PhotoQualityScoring.scorePixels(pixels, 64, 64)
        assertTrue(result.score in 0..100)
        assertTrue(result.sharpness in 0..100)
        assertTrue(result.exposure in 0..100)
        assertTrue(result.contrast in 0..100)
    }
}
