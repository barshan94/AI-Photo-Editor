package com.barshan.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Runs Google's on-device multiclass segmentation neural network.
 *
 * Model labels documented by Google: background, hair, body, face, clothes, and others.
 * This is a semantic segmentation model, not a skin-texture restoration or inpainting model.
 * The model asset is fetched by the Gradle preBuild task from the pinned official URL.
 */
internal object MulticlassSegmentationEngine {
    const val BACKGROUND = 0
    const val HAIR = 1
    const val BODY = 2
    const val FACE = 3
    const val CLOTHES = 4
    const val OTHER = 5

    data class SegmentationMap(
        val width: Int,
        val height: Int,
        /** One class ID (0..5) per mask pixel, row-major. */
        val classes: ByteArray
    ) {
        fun classAt(x: Int, y: Int): Int {
            if (x !in 0 until width || y !in 0 until height) return BACKGROUND
            return classes[y * width + x].toInt() and 0xFF
        }
    }

    suspend fun segment(context: Context, bitmap: Bitmap): SegmentationMap =
        withContext(Dispatchers.Default) {
            require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                "Choose a valid photo before running segmentation."
            }

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("selfie_multiclass_256x256.tflite")
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()

            val segmenter = ImageSegmenter.createFromOptions(context.applicationContext, options)
            var image: MPImage? = null
            try {
                image = BitmapImageBuilder(bitmap).build()
                val result: ImageSegmenterResult = segmenter.segment(image)
                val categoryMask = result.categoryMask().orElseThrow {
                    IllegalStateException("The segmentation model did not return a category mask.")
                }
                val buffer: ByteBuffer = ByteBufferExtractor.extract(categoryMask)
                buffer.rewind()
                val count = categoryMask.width * categoryMask.height
                require(count > 0 && buffer.remaining() >= count) {
                    "The segmentation model returned an invalid mask."
                }
                val classes = ByteArray(count)
                buffer.get(classes, 0, count)
                categoryMask.close()
                SegmentationMap(categoryMask.width, categoryMask.height, classes)
            } finally {
                image?.close()
                segmenter.close()
            }
        }
}
