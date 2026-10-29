package com.barshan.aiphotoeditor

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Runs the official TensorFlow Lite ESRGAN model locally.
 *
 * The published model expects normalized RGB float values in 0..1 and returns normalized RGB
 * values. Overlapping tiles reduce seams; the input is bounded to keep 4x output safe.
 */
internal object AiUpscaler {
    data class Result(val bitmap: Bitmap, val inputWasResizedForMemory: Boolean)

    suspend fun upscale(
        context: Context,
        original: Bitmap,
        onProgress: suspend (percent: Int, stage: String) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.Default) {
        require(!original.isRecycled && original.width > 0 && original.height > 0) {
            "Choose a valid photo before upscaling."
        }
        val maxEdge = maxOf(original.width, original.height)
        val maxPixels = 1024 * 1024
        val scale = minOf(1f, 1536f / maxEdge, kotlin.math.sqrt(maxPixels.toDouble() / (original.width.toDouble() * original.height)).toFloat())
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
            onProgress(3, "Loading AI upscaling engine")
            val modelBytes = context.assets.open("ESRGAN.tflite").use { it.readBytes() }
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
            modelBuffer.put(modelBytes)
            modelBuffer.rewind()
            interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(4))
            onProgress(8, "Preparing image tiles")

            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()
            require(inputShape.size == 4 && outputShape.size == 4 &&
                inputShape[0] == 1 && inputShape[3] == 3 &&
                outputShape[0] == 1 && outputShape[3] == 3) {
                "The upscaling engine has an unsupported input/output shape."
            }
            require(inputTensor.dataType() in setOf(DataType.FLOAT32, DataType.UINT8) &&
                outputTensor.dataType() in setOf(DataType.FLOAT32, DataType.UINT8)) {
                "The installed upscaling model uses an unsupported pixel format."
            }
            val tileSize = inputShape[1]
            val tileWidth = inputShape[2]
            val outputTileHeight = outputShape[1]
            val outputTileWidth = outputShape[2]
            require(tileSize == tileWidth && outputTileWidth % tileWidth == 0 &&
                outputTileHeight % tileSize == 0) {
                "The upscaling engine uses an unsupported tile shape."
            }
            val scaleX = outputTileWidth / tileWidth
            val scaleY = outputTileHeight / tileSize
            val outWidth = inputBitmap.width * scaleX
            val outHeight = inputBitmap.height * scaleY
            require(outWidth.toLong() * outHeight.toLong() <= 16_777_216L) {
                "The 4x result would be too large for safe on-device processing. Try a smaller source photo."
            }

            val inputPixels = IntArray(tileSize * tileWidth)
            val inputBytesPerChannel = if (inputTensor.dataType() == DataType.FLOAT32) 4 else 1
            val outputBytesPerChannel = if (outputTensor.dataType() == DataType.FLOAT32) 4 else 1
            val input = ByteBuffer.allocateDirect(tileSize * tileWidth * 3 * inputBytesPerChannel).order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(outputTileHeight * outputTileWidth * 3 * outputBytesPerChannel).order(ByteOrder.nativeOrder())
            val tileOutput = IntArray(outputTileHeight * outputTileWidth)
            val outputPixels = IntArray(outWidth * outHeight)

            // Overlap adjacent tiles by 50% and average the shared pixels. Directly
            // placing non-overlapping ESRGAN tiles creates visible square seams.
            val stepX = maxOf(1, tileWidth / 2)
            val stepY = maxOf(1, tileSize / 2)
            val totalTiles = ((inputBitmap.width + stepX - 1) / stepX) *
                ((inputBitmap.height + stepY - 1) / stepY)
            val outputWeights = ShortArray(outWidth * outHeight)
            var completedTiles = 0
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
                        val red = (pixel shr 16) and 0xFF
                        val green = (pixel shr 8) and 0xFF
                        val blue = pixel and 0xFF
                        if (inputTensor.dataType() == DataType.FLOAT32) {
                            // The published float model expects normalized RGB values in 0..1.
                            input.putFloat(red / 255f)
                            input.putFloat(green / 255f)
                            input.putFloat(blue / 255f)
                        } else {
                            // Also support byte-encoded exports of the same RGB model.
                            input.put(red.toByte())
                            input.put(green.toByte())
                            input.put(blue.toByte())
                        }
                    }
                    input.rewind()
                    output.clear()
                    interpreter.run(input, output)
                    output.rewind()
                    for (i in tileOutput.indices) {
                        val red: Int
                        val green: Int
                        val blue: Int
                        if (outputTensor.dataType() == DataType.FLOAT32) {
                            // Float ESRGAN output is normalized RGB; convert back to 8-bit.
                            red = (output.float * 255f).toInt().coerceIn(0, 255)
                            green = (output.float * 255f).toInt().coerceIn(0, 255)
                            blue = (output.float * 255f).toInt().coerceIn(0, 255)
                        } else {
                            red = output.get().toInt() and 0xFF
                            green = output.get().toInt() and 0xFF
                            blue = output.get().toInt() and 0xFF
                        }
                        tileOutput[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                    for (oy in 0 until outputTileHeight) {
                        val dstY = top * scaleY + oy
                        if (dstY >= outHeight) continue
                        val dstX = left * scaleX
                        val count = min(outputTileWidth, outWidth - dstX)
                        if (count > 0) {
                            val sourceOffset = oy * outputTileWidth
                            val overlapY = maxOf(1, stepY * scaleY)
                            val hasTileAbove = top > 0
                            val hasTileBelow = top + tileSize < inputBitmap.height
                            val wy = when {
                                hasTileAbove && oy < overlapY -> (oy + 1f) / (overlapY + 1f)
                                hasTileBelow && oy >= outputTileHeight - overlapY -> (outputTileHeight - oy).toFloat() / (overlapY + 1f)
                                else -> 1f
                            }.coerceIn(0.02f, 1f)
                            val overlapX = maxOf(1, stepX * scaleX)
                            val hasTileLeft = left > 0
                            val hasTileRight = left + tileWidth < inputBitmap.width
                            for (ox in 0 until count) {
                                val destinationIndex = dstY * outWidth + dstX + ox
                                val wx = when {
                                    hasTileLeft && ox < overlapX -> (ox + 1f) / (overlapX + 1f)
                                    hasTileRight && ox >= outputTileWidth - overlapX -> (outputTileWidth - ox).toFloat() / (overlapX + 1f)
                                    else -> 1f
                                }.coerceIn(0.02f, 1f)
                                // 13-bit fixed-point weights retain feathering while using half the memory of FloatArray.
                                val weightUnit = (wx * wy * 8192f).toInt().coerceIn(1, 8192)
                                val nextPixel = tileOutput[sourceOffset + ox]
                                val previousWeight = outputWeights[destinationIndex].toInt() and 0xFFFF
                                if (previousWeight == 0) {
                                    outputPixels[destinationIndex] = nextPixel
                                    outputWeights[destinationIndex] = weightUnit.toShort()
                                } else {
                                    val previousPixel = outputPixels[destinationIndex]
                                    val combinedWeight = previousWeight + weightUnit
                                    val red = (((((previousPixel shr 16) and 255) * previousWeight) + (((nextPixel shr 16) and 255) * weightUnit)) / combinedWeight).coerceIn(0, 255)
                                    val green = (((((previousPixel shr 8) and 255) * previousWeight) + (((nextPixel shr 8) and 255) * weightUnit)) / combinedWeight).coerceIn(0, 255)
                                    val blue = ((((previousPixel and 255) * previousWeight) + ((nextPixel and 255) * weightUnit)) / combinedWeight).coerceIn(0, 255)
                                    outputPixels[destinationIndex] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                                    outputWeights[destinationIndex] = combinedWeight.toShort()
                                }
                            }
                        }
                    }
                    completedTiles++
                    val percent = 8 + (completedTiles * 82 / totalTiles.coerceAtLeast(1))
                    onProgress(percent, "AI upscaling · tile $completedTiles of $totalTiles")
                    left += stepX
                }
                top += stepY
            }
            onProgress(93, "Rebuilding the high-resolution image")
            val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            result.setPixels(outputPixels, 0, outWidth, 0, 0, outWidth, outHeight)
            onProgress(96, "AI reconstruction complete · saving copy")
            Result(result, resized)
        } finally {
            interpreter?.close()
            if (resized && !inputBitmap.isRecycled) inputBitmap.recycle()
        }
    }

}
