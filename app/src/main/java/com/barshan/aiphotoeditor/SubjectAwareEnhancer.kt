package com.barshan.aiphotoeditor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device person-aware enhancement. ML Kit estimates a person mask; edits remain local
 * to this device. If segmentation cannot run, the caller receives a safe standard edit.
 */
object SubjectAwareEnhancer {
    data class Result(val bitmap: Bitmap, val usedAi: Boolean, val message: String? = null)

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    suspend fun enhance(source: Bitmap, settings: EditSettings = EditSettings()): Result {
        var maskInput: Bitmap? = null
        try {
            maskInput = withContext(Dispatchers.Default) {
                val largest = maxOf(source.width, source.height)
                if (largest <= 512) source.copy(Bitmap.Config.ARGB_8888, false)
                else {
                    val scale = 512f / largest
                    Bitmap.createScaledBitmap(
                        source,
                        (source.width * scale).toInt().coerceAtLeast(1),
                        (source.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            }
            val mask = getPersonMask(maskInput!!)
            val enhanced = withContext(Dispatchers.Default) { AutoEnhancer.enhance(source, settings) }
            var blurredBackground: Bitmap? = null
            try {
                if (settings.backgroundBlur > 0f) {
                    blurredBackground = withContext(Dispatchers.Default) {
                        val factor = (1f + settings.backgroundBlur * 0.12f).toInt().coerceIn(2, 4)
                        val small = Bitmap.createScaledBitmap(source,
                            (source.width / factor).coerceAtLeast(1),
                            (source.height / factor).coerceAtLeast(1), true)
                        try { Bitmap.createScaledBitmap(small, source.width, source.height, true) }
                        finally { if (!small.isRecycled) small.recycle() }
                    }
                }
                withContext(Dispatchers.Default) {
                    applyPersonAwareLook(
                        bitmap = enhanced,
                        confidence = mask.values,
                        maskWidth = mask.width,
                        maskHeight = mask.height,
                        sourceWidth = source.width,
                        sourceHeight = source.height,
                        settings = settings,
                        blurredBackground = blurredBackground
                    )
                }
                return Result(enhanced, true)
            } catch (error: Exception) {
                if (!enhanced.isRecycled) enhanced.recycle()
                throw error
            } finally {
                blurredBackground?.let { if (!it.isRecycled) it.recycle() }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return Result(
                bitmap = withContext(Dispatchers.Default) { AutoEnhancer.enhance(source, settings) },
                usedAi = false,
                message = "Person segmentation was unavailable; standard enhancement was used."
            )
        } finally {
            maskInput?.let { if (it !== source && !it.isRecycled) it.recycle() }
        }
    }

    private data class PersonMask(val width: Int, val height: Int, val values: FloatArray)

    private suspend fun getPersonMask(bitmap: Bitmap): PersonMask =
        suspendCancellableCoroutine { continuation ->
            val task = segmenter.process(InputImage.fromBitmap(bitmap, 0))
            task.addOnSuccessListener { mask: SegmentationMask ->
                if (continuation.isActive) {
                    try {
                        val width = mask.width
                        val height = mask.height
                        val values = SegmentationMaskDecoder.decode(mask.buffer, width, height)
                        continuation.resume(PersonMask(width, height, values))
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }.addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    private fun applyPersonAwareLook(
        bitmap: Bitmap,
        confidence: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        settings: EditSettings,
        blurredBackground: Bitmap?
    ) {
        if (maskWidth <= 0 || maskHeight <= 0 || confidence.size < maskWidth * maskHeight) return

        val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val ratio = settings.cropRatio
        val cropWidth: Int
        val cropHeight: Int
        if (ratio == null || ratio <= 0f) {
            cropWidth = sourceWidth
            cropHeight = sourceHeight
        } else if (sourceRatio > ratio) {
            cropHeight = sourceHeight
            cropWidth = (cropHeight * ratio).toInt().coerceIn(1, sourceWidth)
        } else {
            cropWidth = sourceWidth
            cropHeight = (cropWidth / ratio).toInt().coerceIn(1, sourceHeight)
        }
        val left = ((sourceWidth - cropWidth) * ((settings.cropOffsetX.coerceIn(-1f, 1f) + 1f) / 2f))
            .toInt().coerceIn(0, sourceWidth - cropWidth)
        val top = ((sourceHeight - cropHeight) * ((settings.cropOffsetY.coerceIn(-1f, 1f) + 1f) / 2f))
            .toInt().coerceIn(0, sourceHeight - cropHeight)

        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            val sourceY = top + (y.toFloat() / bitmap.height * cropHeight).toInt().coerceIn(0, cropHeight - 1)
            val maskY = (sourceY.toFloat() / sourceHeight * maskHeight).toInt().coerceIn(0, maskHeight - 1)
            for (x in 0 until bitmap.width) {
                val sourceX = left + (x.toFloat() / bitmap.width * cropWidth).toInt().coerceIn(0, cropWidth - 1)
                val maskX = (sourceX.toFloat() / sourceWidth * maskWidth).toInt().coerceIn(0, maskWidth - 1)
                val subject = confidence[maskY * maskWidth + maskX].coerceIn(0f, 1f)
                val pixel = row[x]
                val backgroundPixel = if (blurredBackground != null) {
                    blurredBackground.getPixel(sourceX.coerceIn(0, blurredBackground.width - 1), sourceY.coerceIn(0, blurredBackground.height - 1))
                } else pixel
                val alpha = pixel ushr 24 and 0xFF
                val red = (pixel shr 16 and 0xFF).toDouble()
                val green = (pixel shr 8 and 0xFF).toDouble()
                val blue = (pixel and 0xFF).toDouble()
                val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue
                val saturation = 0.94 + 0.20 * subject
                val contrast = 1.02 + 0.04 * subject
                val brightness = settings.subjectBrightness.coerceIn(0f, 25f).toDouble() * subject
                val blurMix = (settings.backgroundBlur.coerceIn(0f, 20f) / 20f * (1f - subject)).coerceIn(0f, 1f)
                val bgRed = (backgroundPixel shr 16 and 0xFF).toDouble()
                val bgGreen = (backgroundPixel shr 8 and 0xFF).toDouble()
                val bgBlue = (backgroundPixel and 0xFF).toDouble()
                val outRed = ((128.0 + (luma + (red - luma) * saturation - 128.0) * contrast + brightness) * (1.0 - blurMix) + bgRed * blurMix)
                    .toInt().coerceIn(0, 255)
                val outGreen = ((128.0 + (luma + (green - luma) * saturation - 128.0) * contrast + brightness) * (1.0 - blurMix) + bgGreen * blurMix)
                    .toInt().coerceIn(0, 255)
                val outBlue = ((128.0 + (luma + (blue - luma) * saturation - 128.0) * contrast + brightness) * (1.0 - blurMix) + bgBlue * blurMix)
                    .toInt().coerceIn(0, 255)
                row[x] = (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }
}
