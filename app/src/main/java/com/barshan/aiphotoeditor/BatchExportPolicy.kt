package com.barshan.aiphotoeditor

/** Pure batch-export rules kept separate so they can be regression-tested on CI. */
object BatchExportPolicy {
    val presetNames: List<String> = listOf("Auto Level", "Current edits", "Auto", "Natural", "Vivid", "Warm", "Cool", "Mono")

    fun settingsForPreset(name: String): EditSettings = when (name) {
        "Natural" -> EditSettings(brightness = 0f, contrast = 1.03f, saturation = 1.02f, warmth = 0f)
        "Vivid" -> EditSettings(brightness = 0f, contrast = 1.13f, saturation = 1.28f, warmth = 0f)
        "Warm" -> EditSettings(brightness = 2f, contrast = 1.06f, saturation = 1.08f, warmth = 12f)
        "Cool" -> EditSettings(brightness = 0f, contrast = 1.05f, saturation = 1.04f, warmth = -10f)
        "Mono" -> EditSettings(brightness = 0f, contrast = 1.10f, saturation = 0f, warmth = 0f)
        else -> EditSettings()
    }

    fun progressLabel(processed: Int, total: Int, succeeded: Int, failed: Int): String =
        "Exporting ${processed.coerceIn(0, total.coerceAtLeast(0))}/${total.coerceAtLeast(0)} · " +
            "${succeeded.coerceAtLeast(0)} saved · ${failed.coerceAtLeast(0)} failed"

    fun summary(
        succeeded: Int,
        failed: Int,
        skipped: Int,
        preset: String,
        failedItems: List<String> = emptyList()
    ): String {
        val safeSucceeded = succeeded.coerceAtLeast(0)
        val safeFailed = failed.coerceAtLeast(0)
        val safeSkipped = skipped.coerceAtLeast(0)
        val base = "Batch export summary: $safeSucceeded saved, $safeFailed failed, " +
            "$safeSkipped rejected/skipped. Preset: $preset. Destination: Pictures/FRAME AI."
        if (failedItems.isEmpty()) return base
        val shown = failedItems.take(5).joinToString(", ")
        val more = if (failedItems.size > 5) " and ${failedItems.size - 5} more." else ""
        return "$base\nFailed items: $shown$more"
    }
}
