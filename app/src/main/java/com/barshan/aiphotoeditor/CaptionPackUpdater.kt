package com.barshan.aiphotoeditor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads caption data only over Wi-Fi. It never downloads or executes code.
 * The on-device image labeler and edit pipeline remain native app/model components.
 */
object CaptionPackUpdater {
    private const val PREFS = "frame_ai_caption_pack"
    private const val PACK_KEY = "caption_pack_json"
    private const val VERSION_KEY = "caption_pack_version"
    private const val CHECK_KEY = "caption_pack_last_check_ms"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_PACK_BYTES = 1_000_000
    private const val PACK_URL =
        "https://raw.githubusercontent.com/barshan94/AI-Photo-Editor/main/caption-pack.json"

    fun cachedPack(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PACK_KEY, null)

    suspend fun updateIfOnWifi(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(CHECK_KEY, 0L) < CHECK_INTERVAL_MS) return@withContext null

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@withContext null
        val network = connectivity.activeNetwork ?: return@withContext null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return@withContext null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@withContext null

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(PACK_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FRAME-AI-Android")
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                prefs.edit().putLong(CHECK_KEY, now).apply()
                return@withContext null
            }
            val length = connection.contentLength
            if (length > MAX_PACK_BYTES) {
                prefs.edit().putLong(CHECK_KEY, now).apply()
                return@withContext null
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (json.toByteArray(Charsets.UTF_8).size > MAX_PACK_BYTES) {
                prefs.edit().putLong(CHECK_KEY, now).apply()
                return@withContext null
            }
            val root = JSONObject(json)
            val version = root.optInt("version", -1)
            val entries = root.optJSONArray("entries")
            if (version < 1 || entries == null || entries.length() == 0) {
                prefs.edit().putLong(CHECK_KEY, now).apply()
                return@withContext null
            }
            val installedVersion = prefs.getInt(VERSION_KEY, 0)
            prefs.edit().putLong(CHECK_KEY, now).apply()
            if (version <= installedVersion) return@withContext null

            // Validate the pack before caching it; CaptionLibrary also validates every field on load.
            val validCount = (0 until minOf(entries.length(), 1000)).count { index ->
                val item = entries.optJSONObject(index)
                item != null && item.optString("category").isNotBlank() &&
                    item.optString("text").isNotBlank()
            }
            if (validCount == 0) return@withContext null

            prefs.edit()
                .putString(PACK_KEY, json)
                .putInt(VERSION_KEY, version)
                .apply()
            json
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
