package com.barshan.aiphotoeditor

/** Conservative bilingual caption starters based only on labels returned by on-device image labeling. */
object CaptionSuggestionEngine {
    data class Suggestions(
        val detectedLabels: List<String>,
        val english: List<String>,
        val bengali: List<String>
    )

    fun fromLabels(labels: List<Pair<String, Float>>, aspectRatio: Float): Suggestions {
        val trusted = labels
            .filter { it.second >= 0.50f }
            .sortedByDescending { it.second }
            .map { it.first.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        val labelText = trusted.joinToString(" ").lowercase()
        val scene = when {
            listOf("wedding", "bride", "groom", "ceremony", "marriage").any { it in labelText } -> Scene(
                "A beautiful moment, kept forever.",
                "সুন্দর একটি মুহূর্ত, স্মৃতিতে থাকুক চিরকাল।"
            )
            listOf("beach", "ocean", "sea", "waterfall", "mountain", "landscape", "sky", "sunset", "sunrise", "nature", "forest").any { it in labelText } -> Scene(
                "A little pause to take in the beauty around us.",
                "চারপাশের সৌন্দর্য অনুভব করার একটুখানি বিরতি।"
            )
            listOf("flower", "plant", "garden").any { it in labelText } -> Scene(
                "Finding beauty in the little things.",
                "ছোট ছোট জিনিসের মাঝেই লুকিয়ে থাকে সৌন্দর্য।"
            )
            listOf("dog", "cat", "bird", "animal", "pet").any { it in labelText } -> Scene(
                "A little joy with a lot of personality.",
                "একটু আনন্দ, আর ভরপুর নিজস্বতা।"
            )
            listOf("food", "dish", "cuisine", "dessert", "fruit").any { it in labelText } -> Scene(
                "Good things are worth savouring.",
                "ভালো মুহূর্তের মতো, ভালো স্বাদও উপভোগ করার।"
            )
            listOf("building", "architecture", "city", "street", "bridge").any { it in labelText } -> Scene(
                "Stories live in the places we notice.",
                "যে জায়গাগুলো চোখে পড়ে, সেখানেও গল্প থাকে।"
            )
            listOf("person", "people", "face", "portrait", "human", "man", "woman", "child").any { it in labelText } -> Scene(
                "A moment that feels like me.",
                "নিজের মতো করে বাঁচা একটি মুহূর্ত।"
            )
            aspectRatio > 1.15f -> Scene(
                "A wider view, a moment worth keeping.",
                "বিস্তৃত দৃশ্য, মনে রাখার মতো একটি মুহূর্ত।"
            )
            else -> Scene(
                "Keeping a little piece of today.",
                "আজকের ছোট্ট একটি মুহূর্ত ধরে রাখা।"
            )
        }

        val english = listOf(
            scene.english,
            "Through my lens, one moment at a time.",
            "Not just a photo — a memory in the making."
        )
        val bengali = listOf(
            scene.bengali,
            "আমার চোখে দেখা, আমার লেন্সে ধরা।",
            "শুধু ছবি নয়, তৈরি হচ্ছে এক টুকরো স্মৃতি।"
        )
        return Suggestions(trusted, english, bengali)
    }

    private data class Scene(val english: String, val bengali: String)
}
