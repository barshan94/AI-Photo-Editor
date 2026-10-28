package com.barshan.aiphotoeditor

/**
 * Offline caption preset bank. Entries are generated from curated, category-specific
 * phrase sets so search is instant, works without network access, and adds no large runtime dependency.
 * Dataset inspiration/reference: CC0 Flickr8k captions (https://huggingface.co/datasets/intro/flickr8k).
 * This app's social-caption wording below is original; it does not redistribute that dataset's text.
 */
object CaptionLibrary {
    data class Entry(val id: String, val category: String, val text: String, val keywords: Set<String>)

    private val templates = listOf(
        "A little %s goes a long way.",
        "Here for the %s and staying for the memories.",
        "Collecting moments of %s, one frame at a time.",
        "Some days are made for %s.",
        "A frame full of %s.",
        "More %s, less overthinking.",
        "Proof that %s can make an ordinary day special.",
        "Keeping this little moment of %s close.",
        "If happiness had a frame, it might look like %s.",
        "Just a reminder to make room for %s."
    )

    private val topics = linkedMapOf(
        "Portrait" to listOf("quiet confidence", "sunlit smiles", "being unapologetically yourself", "a little self-love", "soft light and honest expressions", "new chapters", "finding your own rhythm", "the details that feel like you", "main-character energy", "feeling good in your own skin"),
        "Friends" to listOf("unplanned laughter", "the usual chaos", "friends who feel like home", "inside jokes", "one more group photo", "making ordinary days legendary", "people who get your weird", "memories with my favourite humans", "laughing until it hurts", "the kind of friendship that stays"),
        "Family" to listOf("the people who know every version of me", "little family traditions", "being together", "home in human form", "generations in one frame", "the familiar kind of happiness", "family stories", "a table full of love", "moments worth passing down", "the people I call first"),
        "Festival" to listOf("festival lights", "colour everywhere", "the joy of dressing up", "traditions that bring us together", "the crowd and the excitement", "one more round of celebrations", "the magic of the season", "old rituals and new memories", "the lights after sunset", "a day we waited for"),
        "Travel" to listOf("roads with no rush", "new places and open skies", "getting a little lost", "views worth the journey", "a map full of possibilities", "slow mornings away", "unexpected turns", "collecting places, not things", "the long way around", "a change of scenery"),
        "Nature" to listOf("golden-hour skies", "quiet green corners", "the sound of stillness", "wild little details", "fresh air and open space", "light through the leaves", "clouds doing their thing", "the calm after the rain", "small wonders outdoors", "a world moving at its own pace"),
        "Food" to listOf("good food and better company", "the first bite", "comfort on a plate", "something made with love", "weekend cravings", "a table worth gathering around", "the little details on the plate", "sharing one more bite", "the joy of trying something new", "a meal that became a memory"),
        "Pets" to listOf("paws and personality", "tiny moments with my favourite fluff", "unconditional love", "the cutest kind of chaos", "walks with a happy sidekick", "a face that fixes any day", "four paws and a full heart", "the boss of this house", "the best welcome home", "more treats, please"),
        "Wedding" to listOf("a promise in one frame", "the beginning of our next chapter", "love in the little details", "a day we will tell stories about", "two lives choosing the same direction", "the happy tears", "a celebration of us", "the people who made the day brighter", "a moment that felt unreal", "memories dressed up in love"),
        "Aesthetic" to listOf("soft tones and slower moments", "shadows doing their own art", "little details worth noticing", "the beauty of the ordinary", "a quiet kind of magic", "light, texture, and mood", "the in-between moments", "simple things done beautifully", "a frame that feels like a deep breath", "less noise and more feeling")
    )

    @Volatile
    private var remoteEntries: List<Entry> = emptyList()

    private val bundledEntries: List<Entry> by lazy {
        buildList {
            topics.forEach { (category, categoryTopics) ->
                categoryTopics.forEachIndexed { topicIndex, topic ->
                    templates.forEachIndexed { templateIndex, template ->
                        val text = template.format(topic)
                        val keywords = (topic.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) +
                            category.lowercase() + "caption" + "photo" + "memory").filter { it.length > 1 }.toSet()
                        add(Entry("$category-$topicIndex-$templateIndex", category, text, keywords))
                    }
                }
            }
        }.distinctBy { it.text }.take(1000)
    }

    val all: List<Entry>
        get() = (bundledEntries + remoteEntries).distinctBy { it.text }.take(2000)

    /** Installs validated caption data downloaded from our versioned pack; executable code is never downloaded. */
    fun installRemotePack(json: String?) {
        if (json.isNullOrBlank()) {
            remoteEntries = emptyList()
            return
        }
        remoteEntries = try {
            val root = org.json.JSONObject(json)
            val array = root.optJSONArray("entries") ?: return
            buildList {
                for (index in 0 until minOf(array.length(), 1000)) {
                    val item = array.optJSONObject(index) ?: continue
                    val category = item.optString("category").trim().take(40)
                    val text = item.optString("text").trim().take(240)
                    if (category.isBlank() || text.isBlank()) continue
                    val keywordsJson = item.optJSONArray("keywords")
                    val keywords = buildSet {
                        add(category.lowercase())
                        if (keywordsJson != null) {
                            for (keyIndex in 0 until minOf(keywordsJson.length(), 20)) {
                                val keyword = keywordsJson.optString(keyIndex).trim().lowercase()
                                if (keyword.length in 2..50) add(keyword)
                            }
                        }
                    }
                    add(Entry("remote-$index", category, text, keywords))
                }
            }.distinctBy { it.text }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun search(query: String, category: String = "All", limit: Int = 40): List<Entry> {
        val normalized = query.trim().lowercase()
        return all.asSequence()
            .filter { category == "All" || it.category == category }
            .filter { normalized.isBlank() || it.text.lowercase().contains(normalized) ||
                it.category.lowercase().contains(normalized) || it.keywords.any { keyword -> keyword.contains(normalized) } }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    /** Rank local presets against visual labels and the user's selected caption style. */
    fun recommend(labels: List<String>, theme: String = "Auto", limit: Int = 5): List<Entry> {
        val labelText = labels.joinToString(" ").lowercase()
        val category = when {
            theme in topics.keys -> theme
            labelText.contains("durga") || labelText.contains("puja") || labelText.contains("festival") -> "Festival"
            labelText.contains("wedding") || labelText.contains("bride") || labelText.contains("groom") -> "Wedding"
            labelText.contains("dog") || labelText.contains("cat") || labelText.contains("pet") -> "Pets"
            labelText.contains("food") || labelText.contains("cake") || labelText.contains("meal") -> "Food"
            labelText.contains("mountain") || labelText.contains("beach") || labelText.contains("travel") -> "Travel"
            labelText.contains("tree") || labelText.contains("flower") || labelText.contains("forest") -> "Nature"
            labelText.contains("family") || labelText.contains("parent") -> "Family"
            labelText.contains("friend") || labelText.contains("group") || labelText.contains("crowd") -> "Friends"
            labelText.contains("person") || labelText.contains("portrait") || labelText.contains("face") -> "Portrait"
            else -> "Aesthetic"
        }
        val tokens = Regex("[^\\p{L}\\p{N}]+").split(labelText).filter { it.length > 2 }.toSet()
        return all.asSequence()
            .filter { it.category == category }
            .sortedByDescending { entry ->
                entry.keywords.count { keyword -> tokens.any { token -> keyword.contains(token) || token.contains(keyword) } }
            }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    val categories: List<String> get() = listOf("All") + topics.keys.toList()
}
