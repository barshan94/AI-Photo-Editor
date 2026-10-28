package com.barshan.aiphotoeditor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/**
 * First on-device semantic-analysis pass for FRAME AI.
 *
 * This is intentionally advisory: it describes likely visual content and editing
 * opportunities, but never changes pixels or automatically removes a person/object.
 */
internal data class PhotoAnalysisResult(
    val width: Int,
    val height: Int,
    val labels: List<String>,
    val faceCount: Int,
    val subjectSummary: String,
    val lightingSummary: String,
    val recommendations: List<String>
)

internal object PhotoAnalysisEngine {
    suspend fun analyze(bitmap: Bitmap): PhotoAnalysisResult {
        require(bitmap.width > 0 && bitmap.height > 0) { "The selected image is empty." }

        val input = InputImage.fromBitmap(bitmap, 0)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.06f)
                .build()
        )

        try {
            val labels = labeler.process(input).awaitResult()
                .filter { it.confidence >= 0.45f }
                .sortedByDescending { it.confidence }
                .distinctBy { it.text.lowercase() }
                .take(8)
                .map { "${it.text} (${(it.confidence * 100).toInt()}%)" }

            val faces = detector.process(input).awaitResult()
            val brightness = estimateBrightness(bitmap)
            val faceCount = faces.size
            val subjectSummary = summarizeFaceCount(faceCount)

            val lightingSummary = when {
                brightness < 62 -> "The sampled image looks dark. Check shadow detail and exposure before adding contrast."
                brightness > 196 -> "The sampled image looks bright. Check highlight clipping before increasing exposure."
                else -> "Average brightness looks broadly balanced; local shadows and highlights may still need review."
            }

            val normalizedLabels = labels.map { it.substringBefore(" (").trim().lowercase() }
            val hasLabel: (String) -> Boolean = { term ->
                normalizedLabels.any { it == term || it.contains(term) }
            }
            val isSkyScene = hasLabel("sky") || hasLabel("cloud")
            val isForestScene = hasLabel("forest") || hasLabel("tree") || hasLabel("woodland") ||
                hasLabel("jungle") || hasLabel("plant")
            val isWaterScene = hasLabel("water") || hasLabel("lake") || hasLabel("ocean") ||
                hasLabel("river") || hasLabel("sea")
            val isSunsetScene = hasLabel("sunset") || hasLabel("sunrise")
            val isFoodScene = hasLabel("food") || hasLabel("dish") || hasLabel("meal")

            val recommendations = buildList {
                if (faceCount == 1) {
                    add("Portrait detected: prioritize balanced face exposure and natural skin color; keep real skin texture.")
                    add("Optional portrait retouching: remove temporary blemishes lightly. Makeup and skin brightening should remain user-controlled.")
                } else if (faceCount >= 2) {
                    add("Multiple faces detected: check each face separately and keep everyone inside the crop.")
                    add("Group lighting: avoid brightening one face so much that other faces lose natural color.")
                } else {
                    add("No clear face detected: prioritize the main scene or object instead of applying portrait effects.")
                }

                when {
                    isSkyScene -> add("Sky scene detected: protect bright cloud highlights and tune blue/cyan tones locally when possible; avoid a global filter that darkens the whole photo.")
                    isForestScene -> add("Greenery detected: recover shadow detail and preserve varied greens; add texture only if the foliage is genuinely soft.")
                    isWaterScene -> add("Water scene detected: preserve highlight reflections and tune blue/green tones gently.")
                    isSunsetScene -> add("Warm-light scene detected: preserve the sunset's natural orange/pink gradient instead of warming every pixel.")
                    isFoodScene -> add("Food scene detected: favor accurate warm color and local contrast; avoid oversaturating reds and oranges.")
                    else -> add("Scene-aware edit: use the detected content as a guide, then compare the result with the untouched original.")
                }

                if (brightness < 62) add("Lighting: try a modest brightness lift first; dark areas may contain noise, so avoid aggressive sharpening.")
                if (brightness > 196) add("Highlights: protect bright detail; increasing exposure further may wash out clouds, skin, or light-colored objects.")
                if (bitmap.width < 1600 || bitmap.height < 1600) {
                    add("Resolution: ${bitmap.width} × ${bitmap.height} px. Consider upscaling for a larger output, but reconstructed detail may be estimated rather than recovered.")
                } else {
                    add("Resolution: ${bitmap.width} × ${bitmap.height} px. Inspect at 100% before deciding whether denoising, sharpening, or upscaling is worthwhile.")
                }
                add("Limit: this pass labels the scene and detects faces; it does not yet remove objects, retouch skin, or reconstruct missing detail.")
            }

            return PhotoAnalysisResult(
                width = bitmap.width,
                height = bitmap.height,
                labels = labels,
                faceCount = faceCount,
                subjectSummary = subjectSummary,
                lightingSummary = lightingSummary,
                recommendations = recommendations.distinct()
            )
        } finally {
            labeler.close()
            detector.close()
        }
    }

    internal fun summarizeFaceCount(faceCount: Int): String = when {
        faceCount <= 0 -> "No clear face detected"
        faceCount == 1 -> "Single-person / portrait candidate"
        faceCount == 2 -> "Two-person candidate; relationship unknown"
        else -> "Group-photo candidate ($faceCount faces)"
    }

    private fun estimateBrightness(bitmap: Bitmap): Double {
        val sample = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        var total = 0.0
        try {
            for (y in 0 until sample.height) {
                for (x in 0 until sample.width) {
                    val pixel = sample.getPixel(x, y)
                    val red = (pixel shr 16) and 0xff
                    val green = (pixel shr 8) and 0xff
                    val blue = pixel and 0xff
                    total += 0.2126 * red + 0.7152 * green + 0.0722 * blue
                }
            }
            return total / (sample.width * sample.height)
        } finally {
            if (sample !== bitmap && !sample.isRecycled) sample.recycle()
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resumeWith(Result.success(task.result))
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("On-device analysis failed.")
                    )
                }
            }
        }
}
