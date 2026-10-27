package com.barshan.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Runs the official TensorFlow Lite ESRGAN model locally.
 *
 * The published model expects 50x50 RGB tiles with channel values in 0..255 and returns 200x200
 * tiles. Input is bounded to 1024px on the long edge to keep the 4x output within phone memory.
 */
internal object AiUpscaler {
    data class Result(val bitmap: Bitmap, val inputWasResizedForMemory: Boolean)

    suspend fun upscale(context: Context, original: Bitmap): Result = withContext(Dispatchers.Default) {
        require(!original.isRecycled && original.width > 0 && original.height > 0) {
            "Choose a valid photo before upscaling."
        }
        val maxEdge = maxOf(original.width, original.height)
        val maxPixels = 1024 * 1024
        val scale = minOf(1f, 1024f / maxEdge, kotlin.math.sqrt(maxPixels.toDouble() / (original.width.toDouble() * original.height)).toFloat())
        val resized = scale < 0.999f
        val inputBitmap = if (resized) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else original

        var interpreter: Interpreter? = null
        try {
            val modelBytes = context.assets.open("ESRGAN.tflite").use { it.readBytes() }
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
            modelBuffer.put(modelBytes)
            modelBuffer.rewind()
            interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(4))

            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()
            require(inputShape.size == 4 && outputShape.size == 4 &&
                inputShape[0] == 1 && inputShape[3] == 3 &&
                outputShape[0] == 1 && outputShape[3] == 3) {
                "The downloaded ESRGAN model has an unsupported input/output shape."
            }
            val tileSize = inputShape[1]
            val tileWidth = inputShape[2]
            val outputTileHeight = outputShape[1]
            val outputTileWidth = outputShape[2]
            require(tileSize == tileWidth && outputTileWidth % tileWidth == 0 &&
                outputTileHeight % tileSize == 0) {
                "The downloaded ESRGAN model uses an unsupported tile shape."
            }
            val scaleX = outputTileWidth / tileWidth
            val scaleY = outputTileHeight / tileSize
            val outWidth = inputBitmap.width * scaleX
            val outHeight = inputBitmap.height * scaleY
            require(outWidth.toLong() * outHeight.toLong() <= 16_777_216L) {
                "The 4x result would be too large for safe on-device processing. Try a smaller source photo."
            }

            val inputPixels = IntArray(tileSize * tileWidth)
            val input = ByteBuffer.allocateDirect(tileSize * tileWidth * 3 * 4).order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(outputTileHeight * outputTileWidth * 3 * 4).order(ByteOrder.nativeOrder())
            val tileOutput = IntArray(outputTileHeight * outputTileWidth)
            val outputPixels = IntArray(outWidth * outHeight)

            var top = 0
            while (top < inputBitmap.height) {
                var left = 0
                while (left < inputBitmap.width) {
                    for (ty in 0 until tileSize) {
                        val sy = min(top + ty, inputBitmap.height - 1)
                        for (tx in 0 until tileWidth) {
                            val sx = min(left + tx, inputBitmap.width - 1)
                            inputPixels[ty * tileWidth + tx] = inputBitmap.getPixel(sx, sy)
                        }
                    }
                    input.clear()
                    for (pixel in inputPixels) {
                        input.putFloat(((pixel shr 16) and 0xFF).toFloat())
                        input.putFloat(((pixel shr 8) and 0xFF).toFloat())
                        input.putFloat((pixel and 0xFF).toFloat())
                    }
                    input.rewind()
                    output.clear()
                    interpreter.run(input, output)
                    output.rewind()
                    for (i in tileOutput.indices) {
                        val red = output.float.toInt().coerceIn(0, 255)
                        val green = output.float.toInt().coerceIn(0, 255)
                        val blue = output.float.toInt().coerceIn(0, 255)
                        tileOutput[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                    for (oy in 0 until outputTileHeight) {
                        val dstY = top * scaleY + oy
                        if (dstY >= outHeight) continue
                        val dstX = left * scaleX
                        val count = min(outputTileWidth, outWidth - dstX)
                        if (count > 0) {
                            System.arraycopy(tileOutput, oy * outputTileWidth, outputPixels, dstY * outWidth + dstX, count)
                        }
                    }
                    left += tileWidth
                }
                top += tileSize
            }
            val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            result.setPixels(outputPixels, 0, outWidth, 0, 0, outWidth, outHeight)
            Result(result, resized)
        } finally {
            interpreter?.close()
            if (resized && !inputBitmap.isRecycled) inputBitmap.recycle()
        }
    }
}
