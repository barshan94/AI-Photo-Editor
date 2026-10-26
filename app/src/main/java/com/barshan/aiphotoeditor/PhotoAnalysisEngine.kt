package com.barshan.aiphotoeditor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabelerOptions
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
            val subjectSummary = when (faceCount) {
                0 -> "No clear face detected. This may be a landscape, object, animal, or a photo where faces are not visible."
                1 -> "One face detected — likely a single-person or portrait candidate."
                2 -> "Two faces detected — could be a couple, friends, or a small group. The model cannot infer their relationship."
                else -> "$faceCount faces detected — group-photo candidate. Check that every face stays inside the crop."
            }

            val lightingSummary = when {
                brightness < 62 -> "The sampled image looks dark. Check shadow detail and exposure before adding contrast."
                brightness > 196 -> "The sampled image looks bright. Check highlight clipping before increasing exposure."
                else -> "Average brightness looks broadly balanced; local shadows and highlights may still need review."
            }

            val recommendations = buildList {
                if (faceCount == 1) {
                    add("Portrait: inspect face exposure and background separation. AI Portrait remains an optional edit.")
                    add("Retouching: review skin detail at full resolution; avoid smoothing away natural texture.")
                } else if (faceCount >= 2) {
                    add("People/group: inspect every face at full size and keep all subjects inside the crop.")
                    add("Light balance: compare exposure across faces before applying a stronger preset.")
                } else {
                    add("Scene/object: review the detected labels and decide whether the main subject or environment needs emphasis.")
                }

                if (brightness < 62) add("Lighting: try a modest brightness lift first; avoid amplifying noise in dark areas.")
                if (brightness > 196) add("Highlights: reduce brightness/exposure if bright details are losing texture.")
                if (labels.any { it.contains("outdoor", true) || it.contains("sky", true) || it.contains("nature", true) }) {
                    add("White balance: check neutral objects and skin tones before warming or cooling the whole image.")
                }
                if (bitmap.width < 1600 || bitmap.height < 1600) {
                    add("Resolution: the image is under 1600 px on at least one side. Upscaling may enlarge it, but cannot restore detail that was never captured.")
                } else {
                    add("Detail: inspect sharpness at 100% before deciding whether sharpening or upscaling is needed.")
                }
                add("Cleanup: this analysis does not identify an unwanted person with certainty or remove objects. Review manually before using a removal tool.")
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
