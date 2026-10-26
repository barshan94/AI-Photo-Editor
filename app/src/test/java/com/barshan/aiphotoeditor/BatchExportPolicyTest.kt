package com.barshan.aiphotoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchExportPolicyTest {
    @Test
    fun exposesAllSupportedPresetsInStableOrder() {
        assertEquals(
            listOf("Current edits", "Auto", "Natural", "Vivid", "Warm", "Cool", "Mono"),
            BatchExportPolicy.presetNames
        )
    }

    @Test
    fun presetsHaveExpectedDistinctAdjustments() {
        val natural = BatchExportPolicy.settingsForPreset("Natural")
        val vivid = BatchExportPolicy.settingsForPreset("Vivid")
        val warm = BatchExportPolicy.settingsForPreset("Warm")
        val cool = BatchExportPolicy.settingsForPreset("Cool")
        val mono = BatchExportPolicy.settingsForPreset("Mono")

        assertTrue(vivid.saturation > natural.saturation)
        assertTrue(vivid.contrast > natural.contrast)
        assertTrue(warm.warmth > 0f)
        assertTrue(cool.warmth < 0f)
        assertEquals(0f, mono.saturation, 0f)
        assertEquals(EditSettings(), BatchExportPolicy.settingsForPreset("Auto"))
        assertEquals(EditSettings(), BatchExportPolicy.settingsForPreset("unknown"))
    }

    @Test
    fun progressNeverExceedsTotalAndHandlesNegativeInputs() {
        assertEquals("Exporting 3/3 · 2 saved · 1 failed",
            BatchExportPolicy.progressLabel(7, 3, 2, 1))
        assertEquals("Exporting 0/0 · 0 saved · 0 failed",
            BatchExportPolicy.progressLabel(-2, -1, -3, -4))
    }

    @Test
    fun summaryReportsCountsAndLimitsFailureDetailsToFive() {
        val summary = BatchExportPolicy.summary(
            succeeded = 4,
            failed = 6,
            skipped = 2,
            preset = "Vivid",
            failedItems = listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg", "f.jpg")
        )
        assertTrue(summary.contains("4 saved, 6 failed, 2 rejected/skipped"))
        assertTrue(summary.contains("Preset: Vivid"))
        assertTrue(summary.contains("a.jpg, b.jpg, c.jpg, d.jpg, e.jpg and 1 more."))
        assertFalse(summary.contains("e.jpg, f.jpg"))
    }

    @Test
    fun summaryOmitsFailureSectionWhenNoItemsFailed() {
        val summary = BatchExportPolicy.summary(3, 0, 1, "Natural")
        assertTrue(summary.contains("3 saved, 0 failed, 1 rejected/skipped"))
        assertFalse(summary.contains("Failed items:"))
    }
}
