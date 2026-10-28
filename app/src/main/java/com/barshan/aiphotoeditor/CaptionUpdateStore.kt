package com.barshan.aiphotoeditor

import android.content.Context
import org.json.JSONArray

object CaptionUpdateStore {
    data class UpdatedCaption(val id: String, val category: String, val text: String)

    fun read(context: Context): List<UpdatedCaption> {
        val raw = context.getSharedPreferences(CaptionUpdateWorker.PREFS, Context.MODE_PRIVATE)
            .getString("caption_pack_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val text = item.optString("text").trim()
                    if (text.isNotBlank()) add(UpdatedCaption(
                        item.optString("id", "updated-$i"),
                        item.optString("category", "General"), text
                    ))
                }
            }
        } catch (_: Exception) { emptyList() }
    }
}
