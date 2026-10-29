package com.barshan.aiphotoeditor

/** User-controlled adjustments shared by single-photo and batch exports. */
data class EditSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1.07f,
    val saturation: Float = 1.06f,
    val warmth: Float = 0f,
    val cropRatio: Float? = null,
    /** Optional independent crop width/height fractions for free-size cropping (0..1). */
    val freeCropWidthFraction: Float? = null,
    val freeCropHeightFraction: Float? = null,
    /** -1..1 positions the crop window within the available image bounds. */
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val straightenDegrees: Float = 0f,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    /** Independent normalized inset controls for each perspective-crop corner (0..0.35). */
    val perspectiveTopLeftX: Float = 0f,
    val perspectiveTopLeftY: Float = 0f,
    val perspectiveTopRightX: Float = 0f,
    val perspectiveTopRightY: Float = 0f,
    val perspectiveBottomRightX: Float = 0f,
    val perspectiveBottomRightY: Float = 0f,
    val perspectiveBottomLeftX: Float = 0f,
    val perspectiveBottomLeftY: Float = 0f,
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
