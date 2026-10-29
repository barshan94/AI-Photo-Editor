package com.barshan.aiphotoeditor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Downloads large optional on-device models into private app storage on first use. */
internal object AiModelStore {
    enum class Model(val fileName: String, val downloadUrl: String, val minimumBytes: Long, val displayName: String) {
        LAMA_INPAINTING(
            "lama_512_int8.onnx",
            "https://huggingface.co/g-ronimo/lama/resolve/main/lama_512_int8.onnx",
            20_000_000L,
            "AI object-removal model"
        ),
        MULTICLASS_SEGMENTATION(
            "selfie_multiclass_256x256.tflite",
            "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/1/selfie_multiclass_256x256.tflite",
            10_000_000L,
            "AI portrait and skin-segmentation model"
        )
    }

    private val downloadMutex = Mutex()

    suspend fun ensureModelFile(context: Context, model: Model): File = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val modelDirectory = File(context.applicationContext.filesDir, "ai-models")
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
                throw IOException("Could not create private AI model storage.")
            }
            val destination = File(modelDirectory, model.fileName)
            if (destination.isFile && destination.length() >= model.minimumBytes) return@withLock destination
            if (destination.exists() && !destination.delete()) throw IOException("Could not replace an incomplete " + model.displayName + ".")

            val temporary = File(modelDirectory, model.fileName + ".download")
            if (temporary.exists()) temporary.delete()
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 90_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "FrameAI-Android")
                }
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) throw IOException("The model server returned HTTP " + responseCode + ".")
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 16)
                        output.fd.sync()
                    }
                }
                if (!temporary.isFile || temporary.length() < model.minimumBytes) {
                    throw IOException("The downloaded " + model.displayName + " was incomplete.")
                }
                try {
                    java.nio.file.Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    if (!temporary.renameTo(destination)) throw IOException("Could not save the downloaded " + model.displayName + ".")
                }
                destination
            } catch (error: Exception) {
                temporary.delete()
                if (error is IOException && error.message?.contains("model", ignoreCase = true) == true) throw error
                throw IOException("Could not download the " + model.displayName + ". Connect to the internet and try again.", error)
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun mapReadOnly(file: File): MappedByteBuffer {
        require(file.isFile && file.length() > 0L) { "The AI model file is missing or empty." }
        return FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
        }
    }
}
