package com.barshan.aiphotoeditor

/**
 * Offline caption preset bank. Entries are generated from curated, category-specific
 * phrase sets so search is instant, works without network access, and adds no large runtime dependency.
 * External dataset candidate for a future factual-image-caption pack: CC0 Flickr8k
 * (https://huggingface.co/datasets/intro/flickr8k). The social-caption presets below are original;
 * no raw rows from that online dataset are bundled in this library.
 */
object CaptionLibrary {
    data class Entry(val id: String, val category: String, val text: String, val bengali: String, val keywords: Set<String>)

    private val templates = listOf(
        "A moment for %s.",
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

    private val templatesBn = listOf(
        "%s-এর জন্য একটা মুহূর্ত।", "%s-এর জন্য এসেছি, স্মৃতি নিয়ে ফিরছি।",
        "একেকটা ফ্রেমে %s-এর স্মৃতি জমাই।", "কিছু দিন শুধু %s-এর জন্যই তৈরি।",
        "%s-এ ভরা একটা ফ্রেম।", "বেশি %s, কম দুশ্চিন্তা।",
        "সাধারণ দিনকেও সুন্দর করে তুলতে পারে %s।", "%s-এর এই ছোট্ট মুহূর্তটা যত্নে রেখে দিলাম।",
        "সুখের যদি একটা ফ্রেম থাকত, হয়তো সেটা %s-এর মতো হতো।",
        "%s-এর জন্য জীবনে একটু জায়গা রাখার কথা মনে করিয়ে দিলাম."
    )
    private val topicsBn = linkedMapOf(
        "Portrait" to listOf("নীরব আত্মবিশ্বাস","রোদমাখা হাসি","নিজের মতো করে থাকা","নিজেকে একটু ভালোবাসা","নরম আলো আর সত্যিকারের অভিব্যক্তি","নতুন অধ্যায়","নিজের ছন্দ খুঁজে পাওয়া","নিজের মতো ছোট ছোট খুঁটিনাটি","জীবনের মূল চরিত্রের অনুভূতি","নিজের ভেতর ভালো লাগা"),
        "Friends" to listOf("হঠাৎ শুরু হওয়া হাসি","চেনা পাগলামি","ঘরের মতো আপন বন্ধুরা","ভেতরের মজার কথা","আরেকটা দলবদ্ধ ছবি","সাধারণ দিনকে স্মরণীয় করা","আমার পাগলামি বোঝে যারা","প্রিয় মানুষদের সঙ্গে স্মৃতি","হাসতে হাসতে চোখে জল","টিকে থাকা বন্ধুত্ব"),
        "Family" to listOf("আমার সব রূপ চেনে যারা","পরিবারের ছোট ছোট রীতি","একসঙ্গে থাকা","মানুষের মাঝে ঘর খুঁজে পাওয়া","এক ফ্রেমে কয়েক প্রজন্ম","চেনা সুখ","পরিবারের গল্প","ভালোবাসায় ভরা টেবিল","পরের প্রজন্মকে বলার মতো মুহূর্ত","যাদের আগে ফোন করি"),
        "Festival" to listOf("উৎসবের আলো","চারদিকে রঙের ছোঁয়া","সেজেগুজে থাকার আনন্দ","যে ঐতিহ্য আমাদের এক করে","ভিড় আর উত্তেজনা","আরেক দফা উদ্‌যাপন","ঋতুর মায়া","পুরোনো রীতি আর নতুন স্মৃতি","সূর্য ডোবার পরের আলো","যে দিনের অপেক্ষায় ছিলাম"),
        "Travel" to listOf("তাড়াহীন পথ","নতুন জায়গা আর খোলা আকাশ","একটু পথ হারানো","যাত্রার যোগ্য দৃশ্য","সম্ভাবনায় ভরা মানচিত্র","দূরে কোথাও ধীর সকাল","অপ্রত্যাশিত মোড়","জিনিস নয়, জায়গার স্মৃতি","ঘুরপথে ফেরার আনন্দ","পরিবেশ বদলে নেওয়া"),
        "Nature" to listOf("সোনালি বিকেলের আকাশ","নিরিবিলি সবুজ কোণ","নীরবতার শব্দ","প্রকৃতির ছোট খুঁটিনাটি","টাটকা বাতাস আর খোলা জায়গা","পাতার ফাঁকে আলো","নিজের মতো ভেসে চলা মেঘ","বৃষ্টির পরের শান্তি","বাইরের ছোট ছোট বিস্ময়","নিজের গতিতে চলা পৃথিবী"),
        "Food" to listOf("ভালো খাবার আর আরও ভালো সঙ্গ","প্রথম কামড়","প্লেটে সাজানো আরাম","ভালোবাসা দিয়ে বানানো কিছু","সপ্তাহান্তের খিদে","যে টেবিলে সবাই জড়ো হয়","প্লেটের ছোট খুঁটিনাটি","আরেক কামড় ভাগ করে নেওয়া","নতুন স্বাদ চেখে দেখার আনন্দ","স্মৃতি হয়ে যাওয়া এক বেলা"),
        "Pets" to listOf("থাবা আর দুষ্টুমি","প্রিয় লোমশ বন্ধুর ছোট মুহূর্ত","নিঃশর্ত ভালোবাসা","সবচেয়ে মিষ্টি পাগলামি","খুশি সঙ্গীকে নিয়ে হাঁটা","যে মুখে দিন ভালো হয়ে যায়","চার পা আর ভরা মন","এই বাড়ির আসল বস","বাড়ি ফেরার সেরা অভ্যর্থনা","আরও একটু ট্রিট"),
        "Wedding" to listOf("এক ফ্রেমে একটি প্রতিশ্রুতি","আমাদের নতুন অধ্যায়ের শুরু","ছোট খুঁটিনাটিতে ভালোবাসা","যে দিনের গল্প বারবার বলব","এক পথে চলতে চাওয়া দুটি জীবন","আনন্দের অশ্রু","আমাদের উদ্‌যাপন","যারা দিনটাকে উজ্জ্বল করেছে","অবিশ্বাস্য মনে হওয়া মুহূর্ত","ভালোবাসায় সাজানো স্মৃতি"),
        "Aesthetic" to listOf("নরম রং আর ধীর মুহূর্ত","নিজের মতো শিল্প করা ছায়া","খেয়াল করার মতো ছোট খুঁটিনাটি","সাধারণের সৌন্দর্য","নীরব এক মায়া","আলো, গঠন আর আবহ","মাঝের সেই মুহূর্তগুলো","সুন্দর করে করা সহজ কাজ","গভীর শ্বাসের মতো এক ফ্রেম","কম কোলাহল আর বেশি অনুভূতি")
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

    val all: List<Entry> by lazy {
        buildList {
            topics.forEach { (category, categoryTopics) ->
                categoryTopics.forEachIndexed { topicIndex, topic ->
                    templates.forEachIndexed { templateIndex, template ->
                        val text = template.format(topic)
                        val bengaliTopic = topicsBn.getValue(category)[topicIndex]
                        val bengali = templatesBn[templateIndex].format(bengaliTopic)
                        val keywords = (topic.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) +
                            bengaliTopic.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) +
                            category.lowercase() + "caption" + "photo" + "memory" + "ক্যাপশন" + "ছবি" + "স্মৃতি")
                            .filter { it.length > 1 }.toSet()
                        add(Entry("$category-$topicIndex-$templateIndex", category, text, bengali, keywords))
                    }
                }
            }
        }.distinctBy { it.text }.take(1000)
    }

    fun search(query: String, category: String = "All", limit: Int = 40): List<Entry> {
        val normalized = query.trim().lowercase()
        val queryTokens = Regex("[^\\p{L}\\p{N}]+").split(normalized).filter { it.length > 1 }
        return all.asSequence()
            .filter { category == "All" || it.category == category }
            .filter { normalized.isBlank() || it.text.lowercase().contains(normalized) ||
                it.bengali.contains(normalized) || it.category.lowercase().contains(normalized) ||
                it.keywords.any { keyword -> keyword.contains(normalized) || queryTokens.any { token -> keyword.contains(token) } } }
            .sortedByDescending { entry ->
                if (normalized.isBlank()) 0 else
                    (if (entry.text.lowercase().startsWith(normalized) || entry.bengali.startsWith(normalized)) 100 else 0) +
                    (if (entry.text.lowercase().contains(normalized) || entry.bengali.contains(normalized)) 30 else 0) +
                    queryTokens.count { token -> entry.keywords.any { it.startsWith(token) } } * 5
            }
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
