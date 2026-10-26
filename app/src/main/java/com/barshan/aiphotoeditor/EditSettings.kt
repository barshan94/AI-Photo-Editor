package com.barshan.aiphotoeditor

/** User-controlled adjustments shared by single-photo and batch exports. */
data class EditSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1.07f,
    val saturation: Float = 1.06f,
    val warmth: Float = 0f,
    val cropRatio: Float? = null
)
