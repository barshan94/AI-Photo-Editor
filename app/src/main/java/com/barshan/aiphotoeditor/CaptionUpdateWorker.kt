package com.barshan.aiphotoeditor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a versioned caption pack on Wi-Fi and preserves the last good copy on failure. */
class CaptionUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!isWifiConnected()) return Result.success()
        return try {
            val connection = (URL(PACK_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FrameAI-Android")
            }
            val body = try {
                if (connection.responseCode !in 200..299) return Result.retry()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally { connection.disconnect() }
            val root = JSONObject(body)
            val version = root.optInt("version", -1)
            val captions = root.optJSONArray("captions") ?: return Result.failure()
            if (version < 1 || captions.length() < MIN_CAPTIONS) return Result.failure()
            val validated = JSONArray()
            val seen = HashSet<String>()
            for (i in 0 until captions.length()) {
                val item = captions.optJSONObject(i) ?: continue
                val text = item.optString("text").trim()
                val category = item.optString("category", "General").trim().ifBlank { "General" }
                if (text.length !in 8..500 || !seen.add(text.lowercase())) continue
                validated.put(JSONObject().put("id", item.optString("id", "remote-$i"))
                    .put("category", category).put("text", text))
            }
            if (validated.length() < MIN_CAPTIONS) return Result.failure()
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (version <= prefs.getInt("caption_pack_version", 0)) return Result.success()
            val saved = prefs.edit().putInt("caption_pack_version", version)
                .putString("caption_pack_json", validated.toString())
                .putLong("caption_pack_updated_at", System.currentTimeMillis()).commit()
            if (saved) Result.success() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }

    private fun isWifiConnected(): Boolean {
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        const val PREFS = "frame_ai_caption_updates"
        private const val MIN_CAPTIONS = 100
        private const val PACK_URL = "https://raw.githubusercontent.com/barshan94/AI-Photo-Editor/main/updates/captions.json"
    }
}
