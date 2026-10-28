package com.barshan.aiphotoeditor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small, versioned online tuning rules for the adaptive on-device AI editor.
 * The photo is still analyzed locally; this file only tunes the selected color intent.
 */
object AiEditRulesRepository {
    data class Rules(
        val version: Int = 1,
        val balancedSaturationMultiplier: Float = 1.0f,
        val balancedContrastAdd: Float = 0.0f,
        val saturatedSaturationMultiplier: Float = 1.18f,
        val saturatedContrastAdd: Float = 0.025f,
        val coolWarmthDelta: Float = -5.0f,
        val coolSaturationMultiplier: Float = 1.02f
    )

    private const val PREFS = "frame_ai_recovery"
    private const val CACHE_KEY = "ai_edit_rules_json"
    private const val RULES_URL =
        "https://raw.githubusercontent.com/barshan94/AI-Photo-Editor/main/ai-edit-rules.json"

    fun readCachedRules(context: Context): Rules {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, null) ?: return Rules()
        return parse(raw) ?: Rules()
    }

    suspend fun refresh(context: Context): Rules = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val cached = readCachedRules(appContext)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RULES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "AI-Photo-Editor-Android")
            }
            if (connection.responseCode !in 200..299) return@withContext cached
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.length > 16_000) return@withContext cached
            val parsed = parse(body) ?: return@withContext cached
            if (parsed.version < cached.version) return@withContext cached
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CACHE_KEY, body)
                .putLong("ai_edit_rules_updated_ms", System.currentTimeMillis())
                .commit()
            parsed
        } catch (_: Exception) {
            cached
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(raw: String): Rules? {
        return try {
            val root = JSONObject(raw)
            val version = root.optInt("version", 0)
            if (version < 1) {
                null
            } else {
                val balanced = root.optJSONObject("balanced") ?: JSONObject()
                val saturated = root.optJSONObject("saturated") ?: JSONObject()
                val cool = root.optJSONObject("cool") ?: JSONObject()
                Rules(
                    version = version,
                    balancedSaturationMultiplier = balanced.optDouble("saturationMultiplier", 1.0).toFloat().coerceIn(0.85f, 1.15f),
                    balancedContrastAdd = balanced.optDouble("contrastAdd", 0.0).toFloat().coerceIn(-0.05f, 0.05f),
                    saturatedSaturationMultiplier = saturated.optDouble("saturationMultiplier", 1.18).toFloat().coerceIn(1.0f, 1.45f),
                    saturatedContrastAdd = saturated.optDouble("contrastAdd", 0.025).toFloat().coerceIn(0f, 0.08f),
                    coolWarmthDelta = cool.optDouble("warmthDelta", -5.0).toFloat().coerceIn(-12f, 0f),
                    coolSaturationMultiplier = cool.optDouble("saturationMultiplier", 1.02).toFloat().coerceIn(0.9f, 1.15f)
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
