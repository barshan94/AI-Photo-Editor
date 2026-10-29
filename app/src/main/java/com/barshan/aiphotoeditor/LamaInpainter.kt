package com.barshan.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Local LaMa object-removal inference. Mask polarity is 1 = remove, 0 = preserve. */
internal object LamaInpainter {
    data class Result(val bitmap: Bitmap, val markedPixels: Int)

    suspend fun inpaint(
        context: Context,
        source: Bitmap,
        normalizedStrokes: List<List<Offset>>,
        brushRadius: Float,
        selectionMask: Bitmap? = null
    ): Result = withContext(Dispatchers.Default) {
        require(normalizedStrokes.any { it.isNotEmpty() } || selectionMask != null) {
            "Select a person or paint over the object you want to remove first."
        }
        require(!source.isRecycled && source.width > 0 && source.height > 0) { "Choose a valid photo first." }

        val modelSize = 512
        val fullMask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(2f, brushRadius * 2f * source.width / modelSize)
        }
        val maskCanvas = Canvas(fullMask)
        if (selectionMask != null && !selectionMask.isRecycled) {
            maskCanvas.drawBitmap(
                selectionMask,
                null,
                Rect(0, 0, source.width, source.height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }
        for (stroke in normalizedStrokes) {
            if (stroke.isEmpty()) continue
            if (stroke.size == 1) {
                maskCanvas.drawCircle(
                    stroke[0].x.coerceIn(0f, 1f) * source.width,
                    stroke[0].y.coerceIn(0f, 1f) * source.height,
                    paint.strokeWidth / 2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.FILL }
                )
            } else {
                val path = Path().apply {
                    moveTo(stroke[0].x.coerceIn(0f, 1f) * source.width, stroke[0].y.coerceIn(0f, 1f) * source.height)
                    stroke.drop(1).forEach { lineTo(it.x.coerceIn(0f, 1f) * source.width, it.y.coerceIn(0f, 1f) * source.height) }
                }
                maskCanvas.drawPath(path, paint)
            }
        }
        // Preserve the source aspect ratio. Stretching every portrait/landscape photo into
        // a square distorts scene geometry and makes LaMa's reconstruction look wrong.
        val fitScale = min(modelSize.toFloat() / source.width, modelSize.toFloat() / source.height)
        val fitWidth = (source.width * fitScale).roundToInt().coerceIn(1, modelSize)
        val fitHeight = (source.height * fitScale).roundToInt().coerceIn(1, modelSize)
        val offsetX = (modelSize - fitWidth) / 2
        val offsetY = (modelSize - fitHeight) / 2
        val contentRect = Rect(offsetX, offsetY, offsetX + fitWidth, offsetY + fitHeight)
        val image512 = Bitmap.createBitmap(modelSize, modelSize, Bitmap.Config.ARGB_8888)
        Canvas(image512).apply {
            drawColor(AndroidColor.BLACK)
            drawBitmap(source, null, contentRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        val scaledMask = Bitmap.createBitmap(modelSize, modelSize, Bitmap.Config.ARGB_8888)
        Canvas(scaledMask).drawBitmap(fullMask, null, contentRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        // Keep the inference mask crisp, but feather the final composite edge so the repaired
        // region does not look like a pasted rectangle or leave a hard brush outline.
        val featherWidth = (source.width / 8).coerceAtLeast(1)
        val featherHeight = (source.height / 8).coerceAtLeast(1)
        val lowResolutionMask = Bitmap.createScaledBitmap(fullMask, featherWidth, featherHeight, true)
        val compositeMask = Bitmap.createScaledBitmap(lowResolutionMask, source.width, source.height, true)
        if (lowResolutionMask !== compositeMask) lowResolutionMask.recycle()
        var session: OrtSession? = null
        var imageTensor: OnnxTensor? = null
        try {
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            val modelBytes = context.assets.open("lama_512_int8.onnx").use { it.readBytes() }
            try {
                sessionOptions.setIntraOpNumThreads(2)
                session = env.createSession(modelBytes, sessionOptions)
            } finally {
                sessionOptions.close()
            }

            val imagePixels = IntArray(modelSize * modelSize)
            val maskPixels = IntArray(modelSize * modelSize)
            image512.getPixels(imagePixels, 0, modelSize, 0, 0, modelSize, modelSize)
            scaledMask.getPixels(maskPixels, 0, modelSize, 0, 0, modelSize, modelSize)
            val inputValues = FloatArray(4 * modelSize * modelSize)
            val plane = modelSize * modelSize
            var marked = 0
            for (i in imagePixels.indices) {
                val alpha = AndroidColor.alpha(maskPixels[i])
                val erase = if (alpha >= 24) 1f else 0f
                if (erase > 0f) marked++
                val pixel = imagePixels[i]
                inputValues[i] = if (erase > 0f) 0f else AndroidColor.red(pixel) / 255f
                inputValues[plane + i] = if (erase > 0f) 0f else AndroidColor.green(pixel) / 255f
                inputValues[2 * plane + i] = if (erase > 0f) 0f else AndroidColor.blue(pixel) / 255f
                inputValues[3 * plane + i] = erase
            }
            require(marked > 0) { "The brush mask was too small. Paint over the object with a thicker brush." }

            val imageBuffer = ByteBuffer.allocateDirect(inputValues.size * 4).order(ByteOrder.nativeOrder())
            val imageFloatBuffer: FloatBuffer = imageBuffer.asFloatBuffer()
            imageFloatBuffer.put(inputValues).rewind()
            imageTensor = OnnxTensor.createTensor(env, imageFloatBuffer, longArrayOf(1, 4, modelSize.toLong(), modelSize.toLong()))
            val outputs = session.run(mapOf("input" to imageTensor))
            try {
                val outputTensor = outputs.get("output").orElseThrow { IllegalStateException("LaMa did not return an output image.") } as OnnxTensor
                val outputBuffer = outputTensor.floatBuffer
                outputBuffer.rewind()
                val outputValues = FloatArray(3 * plane)
                outputBuffer.get(outputValues)
                val repairedPixels = IntArray(plane)
                for (i in 0 until plane) {
                    val red = (outputValues[i] * 255f).toInt().coerceIn(0, 255)
                    val green = (outputValues[plane + i] * 255f).toInt().coerceIn(0, 255)
                    val blue = (outputValues[2 * plane + i] * 255f).toInt().coerceIn(0, 255)
                    repairedPixels[i] = AndroidColor.rgb(red, green, blue)
                }
                val repaired512 = Bitmap.createBitmap(modelSize, modelSize, Bitmap.Config.ARGB_8888)
                repaired512.setPixels(repairedPixels, 0, modelSize, 0, 0, modelSize, modelSize)
                val repairedContent = Bitmap.createBitmap(repaired512, offsetX, offsetY, fitWidth, fitHeight)
                val repairedFull = Bitmap.createScaledBitmap(repairedContent, source.width, source.height, true)
                repairedContent.recycle()
                val result = source.copy(Bitmap.Config.ARGB_8888, true)
                val sourceRow = IntArray(source.width)
                val maskRow = IntArray(source.width)
                val resultRow = IntArray(source.width)
                val repairedRow = IntArray(source.width)
                for (y in 0 until source.height) {
                    source.getPixels(sourceRow, 0, source.width, 0, y, source.width, 1)
                    compositeMask.getPixels(maskRow, 0, source.width, 0, y, source.width, 1)
                    repairedFull.getPixels(repairedRow, 0, source.width, 0, y, source.width, 1)
                        for (x in 0 until source.width) {
                        val alpha = AndroidColor.alpha(maskRow[x]) / 255f
                        if (alpha <= 0f) {
                            resultRow[x] = sourceRow[x]
                        } else {
                            val repaired = repairedRow[x]
                            val original = sourceRow[x]
                            val r = (AndroidColor.red(original) * (1f - alpha) + AndroidColor.red(repaired) * alpha).toInt().coerceIn(0, 255)
                            val g = (AndroidColor.green(original) * (1f - alpha) + AndroidColor.green(repaired) * alpha).toInt().coerceIn(0, 255)
                            val b = (AndroidColor.blue(original) * (1f - alpha) + AndroidColor.blue(repaired) * alpha).toInt().coerceIn(0, 255)
                            resultRow[x] = AndroidColor.rgb(r, g, b)
                        }
                    }
                    result.setPixels(resultRow, 0, source.width, 0, y, source.width, 1)
                }
                repaired512.recycle()
                repairedFull.recycle()
                Result(result, marked)
            } finally {
                outputs.close()
            }
        } finally {
            imageTensor?.close()
            session?.close()
            fullMask.recycle()
            compositeMask.recycle()
            scaledMask.recycle()
            image512.recycle()
        }
    }
}
