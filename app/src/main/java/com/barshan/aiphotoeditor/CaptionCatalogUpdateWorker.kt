package com.barshan.aiphotoeditor

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Refreshes the optional caption pack only on unmetered networks (normally Wi-Fi).
 * The 1,000+ bundled captions always remain available offline.
 */
class CaptionCatalogUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AI-Photo-Editor-Android")
            }
            if (connection.responseCode !in 200..299) return@withContext Result.retry()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.length > MAX_CATALOG_CHARS) return@withContext Result.failure()
            val root = org.json.JSONObject(body)
            val version = root.optInt("version", 0)
            val captions = root.optJSONArray("captions") ?: JSONArray()
            if (version < 1 || captions.length() > MAX_CAPTIONS) return@withContext Result.failure()

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
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousVersion = prefs.getInt("remote_caption_version", 0)
            if (version >= previousVersion && cleaned.isNotEmpty()) {
                val saved = prefs.edit()
                    .putInt("remote_caption_version", version)
                    .putString("remote_caption_entries", cleaned.joinToString("\n"))
                    .putLong("remote_caption_updated_ms", System.currentTimeMillis())
                    .commit()
                if (!saved) return@withContext Result.retry()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val PREFS = "frame_ai_recovery"
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
                    CaptionLibrary.Entry("remote-$index", split[0], split[1], words)
                }
            }.toList()
        }

        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context.applicationContext)
            val wifiOnly = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            val firstFetch = OneTimeWorkRequestBuilder<CaptionCatalogUpdateWorker>()
                .setConstraints(wifiOnly).build()
            manager.enqueueUniqueWork("caption-catalog-first-fetch", ExistingWorkPolicy.KEEP, firstFetch)
            val periodic = PeriodicWorkRequestBuilder<CaptionCatalogUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(wifiOnly).build()
            manager.enqueueUniquePeriodicWork(
                "caption-catalog-wifi-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
