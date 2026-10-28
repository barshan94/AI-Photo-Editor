package com.barshan.aiphotoeditor

/** User-controlled adjustments shared by single-photo and batch exports. */
data class EditSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1.07f,
    val saturation: Float = 1.06f,
    val warmth: Float = 0f,
    val cropRatio: Float? = null,
    /** -1..1 positions the crop window within the available image bounds. */
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val straightenDegrees: Float = 0f,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    /** Perspective-crop inset controls, normalized 0..0.28. */
    val perspectiveTopInset: Float = 0f,
    val perspectiveBottomInset: Float = 0f,
    val perspectiveLeftInset: Float = 0f,
    val perspectiveRightInset: Float = 0f,
    /** 0..20 approximate background blur strength; requires person segmentation. */
    val backgroundBlur: Float = 0f,
    /** 0..10 scene-aware contrast and color lift applied only outside the detected person mask. */
    val backgroundEnhancement: Float = 0f,
    /** 0..25 neutral brightness lift applied to detected skin pixels (face and body skin). */
    val subjectBrightness: Float = 0f,
    /** 0..30 adaptive lift that targets darker skin pixels more strongly than already-bright skin. */
    val darkerAreaLift: Float = 0f,
    /** 0..10 restrained smoothing blended only into detected skin pixels. */
    val skinSmoothing: Float = 0f,
    /** 0..10 subtle contour-guided lip and cheek tint; 0 disables makeup. */
    val makeupIntensity: Float = 0f
)
