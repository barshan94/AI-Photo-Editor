package com.barshan.aiphotoeditor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional online caption updates without WorkManager startup hooks.
 * The bundled caption library always works offline; network failures are non-fatal.
 */
object CaptionCatalogRepository {
    private const val PREFS = "frame_ai_recovery"
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/barshan94/AI-Photo-Editor/main/caption-updates.json"
    private const val MAX_CATALOG_CHARS = 250_000
    private const val MAX_CAPTIONS = 2_000

    fun readCachedEntries(context: Context): List<CaptionLibrary.Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("remote_caption_entries", "").orEmpty()
        return raw.lineSequence().mapIndexedNotNull { index, line ->
            val split = line.split('\t', limit = 2)
            if (split.size != 2 || split[0].isBlank() || split[1].isBlank()) null
            else {
                val words = Regex("[^\\p{L}\\p{N}]+").split(split[1].lowercase())
                    .filter { it.length > 1 }.toSet() + split[0].lowercase()
                CaptionLibrary.Entry("remote-$index", split[0], split[1], "বাংলা অনুবাদ উপলভ্য নয়।", words)
            }
        }.toList()
    }

    private fun readBoundedText(input: java.io.InputStream, maxChars: Int): String? {
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val output = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (output.length + count > maxChars) return null
                output.append(buffer, 0, count)
            }
            return output.toString()
        }
    }

    suspend fun refresh(context: Context): List<CaptionLibrary.Entry> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val cached = readCachedEntries(appContext)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AI-Photo-Editor-Android")
            }
            if (connection.responseCode !in 200..299) return@withContext cached
            val body = readBoundedText(connection.inputStream, MAX_CATALOG_CHARS)
                ?: return@withContext cached
            val root = JSONObject(body)
            val version = root.optInt("version", 0)
            val captions = root.optJSONArray("captions") ?: JSONArray()
            if (version < 1 || captions.length() > MAX_CAPTIONS) return@withContext cached

            val cleaned = buildList {
                for (i in 0 until captions.length()) {
                    val item = captions.optJSONObject(i) ?: continue
                    val category = item.optString("category").trim().take(32)
                    val text = item.optString("text").replace('\n', ' ').replace('\r', ' ').trim().take(240)
                    if (category.isNotBlank() && text.length >= 4 && !text.contains('\t')) {
                        add("$category\t$text")
                    }
                }
            }.distinct().take(MAX_CAPTIONS)

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousVersion = prefs.getInt("remote_caption_version", 0)
            val cleanedText = cleaned.joinToString("\n")
            val previousText = prefs.getString("remote_caption_entries", "").orEmpty()
            // Content changes must refresh even when a publisher forgets to bump the version.
            if (cleaned.isNotEmpty() && (version > previousVersion || cleanedText != previousText)) {
                val saved = prefs.edit()
                    .putInt("remote_caption_version", maxOf(version, previousVersion))
                    .putString("remote_caption_entries", cleanedText)
                    .putLong("remote_caption_updated_ms", System.currentTimeMillis())
                    .commit()
                if (!saved) return@withContext cached
            }
            readCachedEntries(appContext)
        } catch (_: Exception) {
            cached
        } finally {
            connection?.disconnect()
        }
    }
}
