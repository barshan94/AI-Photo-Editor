package com.barshan.aiphotoeditor

import android.content.Context
import kotlin.math.roundToInt

/**
 * Learns a private, on-device starting point from edits the user actually exports.
 * This is preference adaptation, not neural-network training and never stores photos.
 */
data class PersonalEditProfile(
    val acceptedEdits: Int,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val backgroundBlur: Float,
    val subjectBrightness: Float,
    val darkerAreaLift: Float,
    val skinSmoothing: Float
) {
    fun toSettings() = EditSettings(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        warmth = warmth,
        backgroundBlur = backgroundBlur,
        subjectBrightness = subjectBrightness,
        darkerAreaLift = darkerAreaLift,
        skinSmoothing = skinSmoothing
    )
}

object PersonalEditLearning {
    private const val PREFS = "personal_edit_learning_v1"
    private const val COUNT = "accepted_count"
    private const val ALPHA = 0.22f

    fun load(context: Context): PersonalEditProfile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PersonalEditProfile(
            acceptedEdits = p.getInt(COUNT, 0),
            brightness = p.getFloat("brightness", 0f),
            contrast = p.getFloat("contrast", 1.07f),
            saturation = p.getFloat("saturation", 1.06f),
            warmth = p.getFloat("warmth", 0f),
            backgroundBlur = p.getFloat("background_blur", 0f),
            subjectBrightness = p.getFloat("subject_brightness", 0f),
            darkerAreaLift = p.getFloat("darker_area_lift", 0f),
            skinSmoothing = p.getFloat("skin_smoothing", 0f)
        )
    }

    /** Call only after the user successfully exports an edited copy. */
    fun recordAccepted(context: Context, settings: EditSettings): PersonalEditProfile {
        val old = load(context)
        val n = old.acceptedEdits
        fun blend(previous: Float, next: Float): Float =
            if (n == 0) next else previous + ALPHA * (next - previous)

        val next = PersonalEditProfile(
            acceptedEdits = n + 1,
            brightness = blend(old.brightness, settings.brightness),
            contrast = blend(old.contrast, settings.contrast),
            saturation = blend(old.saturation, settings.saturation),
            warmth = blend(old.warmth, settings.warmth),
            backgroundBlur = blend(old.backgroundBlur, settings.backgroundBlur),
            subjectBrightness = blend(old.subjectBrightness, settings.subjectBrightness),
            darkerAreaLift = blend(old.darkerAreaLift, settings.darkerAreaLift),
            skinSmoothing = blend(old.skinSmoothing, settings.skinSmoothing)
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(COUNT, next.acceptedEdits)
            .putFloat("brightness", next.brightness)
            .putFloat("contrast", next.contrast)
            .putFloat("saturation", next.saturation)
            .putFloat("warmth", next.warmth)
            .putFloat("background_blur", next.backgroundBlur)
            .putFloat("subject_brightness", next.subjectBrightness)
            .putFloat("darker_area_lift", next.darkerAreaLift)
            .putFloat("skin_smoothing", next.skinSmoothing)
            .apply()
        return next
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
