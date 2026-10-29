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
            onProgress(3, "Loading AI upscaling engine")
            val modelBytes = context.assets.open("ESRGAN.tflite").use { it.readBytes() }
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
            modelBuffer.put(modelBytes)
            modelBuffer.rewind()
            interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(4))
            onProgress(8, "Preparing image tiles")

            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()
            require(inputShape.size == 4 && outputShape.size == 4 &&
                inputShape[0] == 1 && inputShape[3] == 3 &&
                outputShape[0] == 1 && outputShape[3] == 3) {
                "The upscaling engine has an unsupported input/output shape."
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
            val input = ByteBuffer.allocateDirect(tileSize * tileWidth * 3 * 4).order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(outputTileHeight * outputTileWidth * 3 * 4).order(ByteOrder.nativeOrder())
            val tileOutput = IntArray(outputTileHeight * outputTileWidth)
            val outputPixels = IntArray(outWidth * outHeight)

            // Overlap adjacent tiles by 50% and average the shared pixels. Directly
            // placing non-overlapping ESRGAN tiles creates visible square seams.
            val stepX = maxOf(1, tileWidth / 2)
            val stepY = maxOf(1, tileSize / 2)
            val totalTiles = ((inputBitmap.width + stepX - 1) / stepX) *
                ((inputBitmap.height + stepY - 1) / stepY)
            val outputWeights = ByteArray(outWidth * outHeight)
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
                        // ESRGAN's float model expects RGB values normalized to 0..1.
                        input.putFloat(((pixel shr 16) and 0xFF) / 255f)
                        input.putFloat(((pixel shr 8) and 0xFF) / 255f)
                        input.putFloat((pixel and 0xFF) / 255f)
                    }
                    input.rewind()
                    output.clear()
                    interpreter.run(input, output)
                    output.rewind()
                    for (i in tileOutput.indices) {
                        // ESRGAN returns normalized float RGB values. Convert back to 8-bit channels.
                        val red = (output.float * 255f).toInt().coerceIn(0, 255)
                        val green = (output.float * 255f).toInt().coerceIn(0, 255)
                        val blue = (output.float * 255f).toInt().coerceIn(0, 255)
                        tileOutput[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                    for (oy in 0 until outputTileHeight) {
                        val dstY = top * scaleY + oy
                        if (dstY >= outHeight) continue
                        val dstX = left * scaleX
                        val count = min(outputTileWidth, outWidth - dstX)
                        if (count > 0) {
                            val sourceOffset = oy * outputTileWidth
                            for (ox in 0 until count) {
                                val destinationIndex = dstY * outWidth + dstX + ox
                                val nextPixel = tileOutput[sourceOffset + ox]
                                val previousWeight = outputWeights[destinationIndex].toInt() and 0xFF
                                if (previousWeight == 0) {
                                    outputPixels[destinationIndex] = nextPixel
                                } else {
                                    val previousPixel = outputPixels[destinationIndex]
                                    val combinedWeight = previousWeight + 1
                                    val red = ((((previousPixel shr 16) and 0xFF) * previousWeight) +
                                        ((nextPixel shr 16) and 0xFF)) / combinedWeight
                                    val green = ((((previousPixel shr 8) and 0xFF) * previousWeight) +
                                        ((nextPixel shr 8) and 0xFF)) / combinedWeight
                                    val blue = (((previousPixel and 0xFF) * previousWeight) +
                                        (nextPixel and 0xFF)) / combinedWeight
                                    outputPixels[destinationIndex] = (0xFF shl 24) or
                                        (red shl 16) or (green shl 8) or blue
                                }
                                outputWeights[destinationIndex] = (previousWeight + 1).coerceAtMost(255).toByte()
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
            // Reduce hard tile-boundary lines left by independent ESRGAN tile inference.
            softenTileSeams(outputPixels, outWidth, outHeight, outputTileWidth, outputTileHeight)
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

    /**
     * Tile inference can create visible grid lines. Feather a narrow band across each join
     * rather than altering the rest of the reconstructed image.
     */
    private fun softenTileSeams(pixels: IntArray, width: Int, height: Int, tileWidth: Int, tileHeight: Int) {
        val radius = 4
        var seamX = tileWidth
        while (seamX < width) {
            for (y in 0 until height) {
                val left = pixels[y * width + (seamX - radius - 1).coerceAtLeast(0)]
                val right = pixels[y * width + (seamX + radius).coerceAtMost(width - 1)]
                val start = (seamX - radius).coerceAtLeast(0)
                val end = (seamX + radius).coerceAtMost(width)
                for (x in start until end) {
                    val t = (x - start).toFloat() / (end - start).coerceAtLeast(1)
                    val index = y * width + x
                    pixels[index] = blendToward(pixels[index], left, right, t)
                }
            }
            seamX += tileWidth
        }
        var seamY = tileHeight
        while (seamY < height) {
            for (x in 0 until width) {
                val top = pixels[(seamY - radius - 1).coerceAtLeast(0) * width + x]
                val bottom = pixels[(seamY + radius).coerceAtMost(height - 1) * width + x]
                val start = (seamY - radius).coerceAtLeast(0)
                val end = (seamY + radius).coerceAtMost(height)
                for (y in start until end) {
                    val t = (y - start).toFloat() / (end - start).coerceAtLeast(1)
                    val index = y * width + x
                    pixels[index] = blendToward(pixels[index], top, bottom, t)
                }
            }
            seamY += tileHeight
        }
    }

    private fun blendToward(pixel: Int, first: Int, second: Int, fraction: Float): Int {
        val amount = 0.22f
        val targetR = (((first shr 16) and 255) * (1f - fraction) + ((second shr 16) and 255) * fraction).toInt()
        val targetG = (((first shr 8) and 255) * (1f - fraction) + ((second shr 8) and 255) * fraction).toInt()
        val targetB = ((first and 255) * (1f - fraction) + (second and 255) * fraction).toInt()
        val red = (((pixel shr 16) and 255) * (1f - amount) + targetR * amount).toInt().coerceIn(0, 255)
        val green = (((pixel shr 8) and 255) * (1f - amount) + targetG * amount).toInt().coerceIn(0, 255)
        val blue = ((pixel and 255) * (1f - amount) + targetB * amount).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
