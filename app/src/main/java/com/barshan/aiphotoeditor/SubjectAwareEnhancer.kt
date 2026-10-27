package com.barshan.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
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

    suspend fun enhance(source: Bitmap, settings: EditSettings = EditSettings(), context: Context? = null): Result {
        var maskInput: Bitmap? = null
        var usedMulticlassModel = false
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
            val mask = if (context != null) {
                try {
                    getMulticlassPersonMask(context, maskInput!!).also { usedMulticlassModel = true }
                } catch (_: Exception) {
                    // Keep portrait blur available if the additional model cannot initialize.
                    getPersonMask(maskInput!!)
                }
            } else {
                getPersonMask(maskInput!!)
            }
            val enhanced = withContext(Dispatchers.Default) { AutoEnhancer.enhance(source, settings) }
            var blurredBackground: Bitmap? = null
            try {
                if (settings.backgroundBlur > 0f) {
                    blurredBackground = withContext(Dispatchers.Default) {
                        createBlurredBackground(source, settings.backgroundBlur, context)
                    }
                }
                withContext(Dispatchers.Default) {
                    applyPersonAwareLook(
                        bitmap = enhanced,
                        confidence = mask.values,
                        skinConfidence = mask.skinValues,
                        maskWidth = mask.width,
                        maskHeight = mask.height,
                        sourceWidth = source.width,
                        sourceHeight = source.height,
                        settings = settings,
                        blurredBackground = blurredBackground
                    )
                }
                return Result(
                    bitmap = enhanced,
                    usedAi = usedMulticlassModel,
                    message = if (usedMulticlassModel) null else
                        "Portrait blur used fallback person segmentation. Skin-only lightening is disabled because the skin mask model did not initialize."
                )
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

    /**
     * Gaussian background blur via Android's RenderScript intrinsic, operating on a
     * reduced-size copy to limit memory. The person mask is composited over it afterward.
     * A visibly stronger downscale/upscale fallback is used if no Context is supplied.
     */
    @Suppress("DEPRECATION")
    private fun createBlurredBackground(source: Bitmap, strength: Float, context: Context?): Bitmap {
        val amount = strength.coerceIn(0f, 20f)
        val scale = minOf(1f, 900f / maxOf(source.width, source.height).toFloat())
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        if (context == null) {
            val factor = (2f + amount * 0.35f).toInt().coerceIn(2, 9)
            val tiny = Bitmap.createScaledBitmap(
                small,
                (small.width / factor).coerceAtLeast(1),
                (small.height / factor).coerceAtLeast(1),
                true
            )
            return try {
                val blurredSmall = Bitmap.createScaledBitmap(tiny, small.width, small.height, true)
                if (small !== source && !small.isRecycled) small.recycle()
                Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true).also {
                    if (!blurredSmall.isRecycled) blurredSmall.recycle()
                }
            } finally {
                if (!tiny.isRecycled) tiny.recycle()
            }
        }

        var rs: RenderScript? = null
        var input: Allocation? = null
        var output: Allocation? = null
        var intrinsic: ScriptIntrinsicBlur? = null
        val blurredSmall = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        try {
            rs = RenderScript.create(context.applicationContext)
            input = Allocation.createFromBitmap(rs, small)
            output = Allocation.createFromBitmap(rs, blurredSmall)
            intrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            intrinsic.setInput(input)
            intrinsic.setRadius((amount * 1.25f).coerceIn(1f, 25f))
            intrinsic.forEach(output)
            output.copyTo(blurredSmall)
            return if (blurredSmall.width == source.width && blurredSmall.height == source.height) {
                blurredSmall
            } else {
                Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true).also {
                    if (!blurredSmall.isRecycled) blurredSmall.recycle()
                }
            }
        } finally {
            intrinsic?.destroy()
            output?.destroy()
            input?.destroy()
            rs?.destroy()
            if (small !== source && !small.isRecycled) small.recycle()
        }
    }

    private data class PersonMask(
        val width: Int,
        val height: Int,
        val values: FloatArray,
        /** Separate skin-only mask when the multiclass model is available. */
        val skinValues: FloatArray = FloatArray(0)
    )

    /**
     * Uses the additional MediaPipe neural network to retain hair, face, body, and clothing
     * while blurring the background. Its output is categorical rather than a skin mask.
     */
    private suspend fun getMulticlassPersonMask(
        context: Context,
        bitmap: Bitmap
    ): PersonMask {
        val segmented = MulticlassSegmentationEngine.segment(context, bitmap)
        val values = FloatArray(segmented.classes.size)
        val skinValues = FloatArray(segmented.classes.size)
        for (index in segmented.classes.indices) {
            when (segmented.classes[index].toInt() and 0xFF) {
                MulticlassSegmentationEngine.HAIR,
                MulticlassSegmentationEngine.BODY,
                MulticlassSegmentationEngine.FACE,
                MulticlassSegmentationEngine.CLOTHES -> values[index] = 1f
            }
            // The official multiclass model labels body-skin and face-skin separately.
            when (segmented.classes[index].toInt() and 0xFF) {
                MulticlassSegmentationEngine.BODY,
                MulticlassSegmentationEngine.FACE -> skinValues[index] = 1f
            }
        }
        // Feather hard category edges slightly to reduce a cut-out look around hair and shoulders.
        val smoothed = values.copyOf()
        val smoothedSkin = skinValues.copyOf()
        for (y in 1 until segmented.height - 1) {
            for (x in 1 until segmented.width - 1) {
                val i = y * segmented.width + x
                var personTotal = 0f
                var skinTotal = 0f
                for (dy in -1..1) for (dx in -1..1) {
                    val neighbor = (y + dy) * segmented.width + x + dx
                    personTotal += values[neighbor]
                    skinTotal += skinValues[neighbor]
                }
                smoothed[i] = (values[i] * 0.55f + (personTotal / 9f) * 0.45f).coerceIn(0f, 1f)
                smoothedSkin[i] = (skinValues[i] * 0.55f + (skinTotal / 9f) * 0.45f).coerceIn(0f, 1f)
            }
        }
        return PersonMask(segmented.width, segmented.height, smoothed, smoothedSkin)
    }


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
        skinConfidence: FloatArray,
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

        val width = bitmap.width
        val height = bitmap.height
        var previousRow = IntArray(width)
        var row = IntArray(width)
        var nextRow = IntArray(width)
        val outputRow = IntArray(width)
        bitmap.getPixels(row, 0, width, 0, 0, width, 1)
        previousRow = row.copyOf()
        if (height > 1) bitmap.getPixels(nextRow, 0, width, 0, 1, width, 1) else nextRow = row.copyOf()
        for (y in 0 until height) {
            val sourceY = top + (y.toFloat() / height * cropHeight).toInt().coerceIn(0, cropHeight - 1)
            val maskY = (sourceY.toFloat() / sourceHeight * maskHeight).toInt().coerceIn(0, maskHeight - 1)
            for (x in 0 until width) {
                val sourceX = left + (x.toFloat() / width * cropWidth).toInt().coerceIn(0, cropWidth - 1)
                val maskX = (sourceX.toFloat() / sourceWidth * maskWidth).toInt().coerceIn(0, maskWidth - 1)
                val maskIndex = maskY * maskWidth + maskX
                val subject = confidence[maskIndex].coerceIn(0f, 1f)
                val skin = if (skinConfidence.size >= maskWidth * maskHeight) skinConfidence[maskIndex].coerceIn(0f, 1f) else 0f
                val pixel = row[x]
                val backgroundPixel = if (blurredBackground != null) blurredBackground.getPixel(sourceX.coerceIn(0, blurredBackground.width - 1), sourceY.coerceIn(0, blurredBackground.height - 1)) else pixel
                val alpha = pixel ushr 24 and 0xFF
                var red = (pixel shr 16 and 0xFF).toDouble()
                var green = (pixel shr 8 and 0xFF).toDouble()
                var blue = (pixel and 0xFF).toDouble()
                val smoothMix = (settings.skinSmoothing.coerceIn(0f, 10f) / 10f * 0.42f * skin).toDouble()
                if (smoothMix > 0.001) {
                    var sumRed = 0.0; var sumGreen = 0.0; var sumBlue = 0.0
                    for (dy in -1..1) {
                        val neighborRow = when (dy) { -1 -> previousRow; 0 -> row; else -> nextRow }
                        for (dx in -1..1) {
                            val neighbor = neighborRow[(x + dx).coerceIn(0, width - 1)]
                            sumRed += (neighbor shr 16 and 0xFF).toDouble()
                            sumGreen += (neighbor shr 8 and 0xFF).toDouble()
                            sumBlue += (neighbor and 0xFF).toDouble()
                        }
                    }
                    red = red * (1.0 - smoothMix) + (sumRed / 9.0) * smoothMix
                    green = green * (1.0 - smoothMix) + (sumGreen / 9.0) * smoothMix
                    blue = blue * (1.0 - smoothMix) + (sumBlue / 9.0) * smoothMix
                }
                val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue
                val overallSkinLift = settings.subjectBrightness.coerceIn(0f, 25f).toDouble() * skin
                val darkPixelFactor = ((175.0 - luma) / 175.0).coerceIn(0.0, 1.0)
                val adaptiveDarkLift = settings.darkerAreaLift.coerceIn(0f, 30f).toDouble() * darkPixelFactor * skin
                val brightness = overallSkinLift + adaptiveDarkLift
                val blurMix = (settings.backgroundBlur.coerceIn(0f, 20f) / 20f * (1f - subject)).coerceIn(0f, 1f)
                val bgRed = (backgroundPixel shr 16 and 0xFF).toDouble()
                val bgGreen = (backgroundPixel shr 8 and 0xFF).toDouble()
                val bgBlue = (backgroundPixel and 0xFF).toDouble()
                val baseRed = luma + (red - luma) + brightness
                val baseGreen = luma + (green - luma) + brightness
                val baseBlue = luma + (blue - luma) + brightness
                val outRed = (baseRed * (1.0 - blurMix) + bgRed * blurMix).toInt().coerceIn(0, 255)
                val outGreen = (baseGreen * (1.0 - blurMix) + bgGreen * blurMix).toInt().coerceIn(0, 255)
                val outBlue = (baseBlue * (1.0 - blurMix) + bgBlue * blurMix).toInt().coerceIn(0, 255)
                outputRow[x] = (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
            }
            bitmap.setPixels(outputRow, 0, width, 0, y, width, 1)
            if (y + 1 < height) {
                val reusable = previousRow
                previousRow = row
                row = nextRow
                nextRow = reusable
                if (y + 2 < height) bitmap.getPixels(nextRow, 0, width, 0, y + 2, width, 1) else nextRow = row.copyOf()
            }
        }
    }
}
