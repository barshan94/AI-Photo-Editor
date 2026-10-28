package com.barshan.aiphotoeditor

/**
 * Caption ideas written to sound like something a person might actually post.
 * On-device image labels are only hints; cultural context is never guessed from clothing alone.
 */
object CaptionSuggestionEngine {
    data class Suggestions(val detectedLabels: List<String>, val english: List<String>, val bengali: List<String>)

    fun fromLabels(labels: List<Pair<String, Float>>, aspectRatio: Float, theme: String = "Auto"): Suggestions {
        val trusted = labels
            .filter { it.second >= 0.30f }
            .sortedByDescending { it.second }
            .map { it.first.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
        val words = trusted.joinToString(" ")
        val festival = has(words, "durga puja", "durga", "bijoya", "bijoya dashami", "dashami", "sindoor", "sindur", "vermilion", "puja pandal", "worship", "goddess", "religious festival", "festival", "idol", "temple")
        val wedding = has(words, "wedding", "bride", "groom", "marriage", "engagement", "wedding ceremony")
        val travel = has(words, "beach", "ocean", "sea", "waterfall", "mountain", "landscape", "sunset", "sunrise", "forest", "travel", "tourist", "cityscape", "street", "bridge")
        val food = has(words, "food", "dish", "cuisine", "dessert", "meal", "restaurant", "cake", "coffee")
        val pets = has(words, "dog", "cat", "bird", "animal", "pet", "puppy", "kitten")
        val nature = has(words, "flower", "plant", "garden", "tree", "forest", "lake", "river")
        val people = has(words, "person", "people", "face", "portrait", "human", "man", "woman", "child", "group", "crowd", "family")
        val scene = if (theme != "Auto") theme else when {
            festival -> "Festival"
            wedding -> "Romantic"
            food -> "Food"
            pets -> "Pets"
            travel -> "Travel"
            nature -> "Nature"
            people -> "Family"
            else -> "Aesthetic"
        }

        val en: List<String>
        val bn: List<String>
        when (scene) {
            "Festival" -> {
                en = listOf(
                    "Shubho Bijoya ❤️ Asche bochor abar hobe.",
                    "Bijoya Dashami feels. ❤️",
                    "Puja’s over already? Can we do one more day?",
                    "Sindoor, smiles, and a little bit of goodbye.",
                    "Same people, same puja madness, new memories.",
                    "Ma, see you again next year. 🥹❤️",
                    "The part where nobody’s ready to go home.",
                    "Aaj mon ta ektu beshi kharap… abar dekha hobe, Ma."
                )
                bn = listOf(
                    "শুভ বিজয়া ❤️ আসছে বছর আবার হবে।",
                    "বিজয়া দশমীর অনুভূতিটাই আলাদা। ❤️",
                    "পুজো শেষ? আর একটা দিন হলে কী হতো!",
                    "সিঁদুর, হাসি আর একটু মনখারাপ।",
                    "চেনা মানুষ, পুজোর চেনা পাগলামি, নতুন স্মৃতি।",
                    "মা, আবার দেখা হবে আগামী বছর। 🥹❤️",
                    "যে মুহূর্তে কেউই বাড়ি ফিরতে চায় না।",
                    "আজ মনটা একটু বেশি খারাপ… আবার দেখা হবে, মা।"
                )
            }
            "Romantic" -> {
                en = listOf(
                    "My favourite person, no big caption needed. ❤️",
                    "Just us. That’s the post.",
                    "You make even the random days feel special.",
                    "Keeping this one. And you, obviously.",
                    "Not posing very well, but looking happy together.",
                    "If you’re in the photo, it’s already a favourite.",
                    "A little cheesy, but you’re my person.",
                    "Would choose this moment with you again."
                )
                bn = listOf(
                    "আমার প্রিয় মানুষটা, আর কিছু বলার নেই। ❤️",
                    "শুধু আমরা। এইটুকুই যথেষ্ট।",
                    "তুমি থাকলে সাধারণ দিনও স্পেশাল লাগে।",
                    "এই ছবিটা রাখব। তোমাকেও, অবশ্যই।",
                    "পোজ যেমনই হোক, একসঙ্গে খুশি থাকাটাই আসল।",
                    "ছবিতে তুমি থাকলে এমনিই প্রিয় হয়ে যায়।",
                    "একটু সিনেমাটিক, কিন্তু তুমি আমার মানুষ।",
                    "এই মুহূর্তটা তোমার সঙ্গে আবারও বেছে নিতাম।"
                )
            }
            "Funny" -> {
                en = listOf(
                    "Posting before I find something wrong with it.",
                    "The camera roll has receipts.",
                    "We got one good photo. Please respect the struggle.",
                    "Acting casual took several attempts.",
                    "This was the best one. Don’t ask about the rest.",
                    "Outfit: ready. Pose: still loading.",
                    "No thoughts, just vibes and 23 retakes.",
                    "Proof we can behave for approximately two seconds."
                )
                bn = listOf(
                    "ভুল ধরার আগে পোস্ট করে দিলাম।",
                    "ক্যামেরা রোলে সব প্রমাণ আছে।",
                    "একটা ভালো ছবি উঠেছে, কষ্টটা বুঝুন।",
                    "ক্যাজুয়াল পোজ দিতে কয়েকবার চেষ্টা লেগেছে।",
                    "এটাই সেরা। বাকিগুলো নিয়ে প্রশ্ন করবেন না।",
                    "আউটফিট রেডি, পোজ এখনও লোড হচ্ছে।",
                    "চিন্তা নেই, ভাইব আছে—আর ২৩টা রিটেক।",
                    "প্রায় দুই সেকেন্ড ভদ্র থাকার প্রমাণ।"
                )
            }
            "Devotional" -> {
                en = listOf(
                    "Ma, keep our people safe and our hearts kind. 🙏",
                    "A little prayer, a lot of gratitude.",
                    "May the blessings stay with us long after today.",
                    "Some feelings are better left between you and Ma.",
                    "Pranam, Ma. Until we meet again. ❤️",
                    "Faith feels a little closer on days like this.",
                    "Leaving with folded hands and a full heart."
                )
                bn = listOf(
                    "মা, আমাদের আপনজনদের ভালো রেখো। 🙏",
                    "একটু প্রার্থনা, অনেকটা কৃতজ্ঞতা।",
                    "আজকের পরেও আশীর্বাদ সঙ্গে থাকুক।",
                    "কিছু অনুভূতি মা আর আমার মাঝেই থাক।",
                    "প্রণাম মা। আবার দেখা হবে। ❤️",
                    "এমন দিনে বিশ্বাসটা আরও কাছে মনে হয়।",
                    "জোড়হাতে এলাম, ভরা মন নিয়ে ফিরছি।"
                )
            }
            "Travel" -> {
                en = listOf(
                    "Needed this little escape.",
                    "A good day to be somewhere else.",
                    "Came for the view, stayed a little longer.",
                    "New place, same camera-roll problem.",
                    "Wish I could stay here till sunset.",
                    "A small trip, a very full camera roll.",
                    "Would happily take the long way back."
                )
                bn = listOf(
                    "এমন একটা ছোট্ট বিরতি দরকার ছিল।",
                    "আজ অন্য কোথাও থাকতেই ভালো লাগছিল।",
                    "দৃশ্যটা দেখতে এসে আরও কিছুক্ষণ থেকে গেলাম।",
                    "নতুন জায়গা, ক্যামেরা রোলের সেই পুরোনো সমস্যা।",
                    "ইচ্ছে করছে সূর্য ডোবা পর্যন্ত এখানেই থাকি।",
                    "ছোট্ট একটা ঘোরা, ক্যামেরা রোল ভর্তি।",
                    "ফেরার পথটা একটু লম্বা হলেও আপত্তি নেই।"
                )
            }
            "Family" -> {
                en = listOf(
                    "My people. My favourite kind of noise. ❤️",
                    "A little chaotic, exactly how I like us.",
                    "One for the family album.",
                    "Everyone made it into one frame. Rare win.",
                    "These are the days we’ll talk about later.",
                    "Home looks a lot like these faces.",
                    "Not a perfect picture, a perfect bunch."
                )
                bn = listOf(
                    "আমার মানুষগুলো, আমার প্রিয় কোলাহল। ❤️",
                    "একটু এলোমেলো, ঠিক যেভাবে আমাদের ভালো লাগে।",
                    "ফ্যামিলি অ্যালবামের জন্য একটা ছবি।",
                    "সবাই এক ফ্রেমে—এটাও কম পাওয়া নয়।",
                    "এই দিনগুলোর কথাই পরে বলব।",
                    "এই মুখগুলোই তো ঘরের মতো লাগে।",
                    "ছবি নিখুঁত নয়, মানুষগুলো আমার প্রিয়।"
                )
            }
            "Nature" -> {
                en = listOf(
                    "Stopped for a second. Stayed for the view.",
                    "A little fresh air fixes more than expected.",
                    "No filter, just a really good day outside.",
                    "Could sit here and do absolutely nothing.",
                    "The quiet was the whole point.",
                    "Needed more sky and less screen time.",
                    "Nature showing off again."
                )
                bn = listOf(
                    "একটু থামতে এসেছিলাম, দৃশ্যটার জন্য থেকে গেলাম।",
                    "খোলা হাওয়ায় মনটা অনেকটাই ভালো হয়ে যায়।",
                    "ফিল্টার নেই, বাইরে কাটানো একটা সুন্দর দিন।",
                    "এখানে বসে কিছুই না করে থাকতে পারি।",
                    "এই নীরবতাটাই দরকার ছিল।",
                    "স্ক্রিন কম, আকাশটা আরেকটু বেশি দরকার ছিল।",
                    "প্রকৃতি আবারও নিজের সৌন্দর্য দেখাচ্ছে।"
                )
            }
            "Food" -> {
                en = listOf(
                    "First the photo, then the first bite.",
                    "Would order this again. Immediately.",
                    "Came hungry, leaving very happy.",
                    "Good food deserves a camera-roll spot.",
                    "No sharing until everyone gets a picture.",
                    "This tasted even better than it looks.",
                    "Today’s plans were basically built around this."
                )
                bn = listOf(
                    "আগে ছবি, তারপর প্রথম কামড়।",
                    "এটা আবারও অর্ডার করব—এখনই।",
                    "খিদে নিয়ে এসেছিলাম, খুশি হয়ে ফিরছি।",
                    "ভালো খাবারের ক্যামেরা রোলে জায়গা প্রাপ্য।",
                    "ছবি তোলা শেষ না হওয়া পর্যন্ত ভাগ নেই।",
                    "দেখতে যেমন, খেতেও তার চেয়ে ভালো।",
                    "আজকের প্ল্যানটাই ছিল এটা খাওয়াকে ঘিরে।"
                )
            }
            "Pets" -> {
                en = listOf(
                    "Not my pet. Definitely my favourite photo today.",
                    "Tiny face, absolutely no personal space.",
                    "I took the picture. They stole the show.",
                    "Would cancel plans for this little face.",
                    "The real star of my camera roll.",
                    "Zero idea what’s going on. Still adorable.",
                    "Please tell them I said hi."
                )
                bn = listOf(
                    "আমার পোষ্য না হলেও আজকের প্রিয় ছবি।",
                    "ছোট্ট মুখ, ব্যক্তিগত দূরত্বের কোনো ধারণা নেই।",
                    "ছবিটা আমি তুলেছি, নজর কেড়েছে ও।",
                    "এই মুখটার জন্য প্ল্যান বাতিল করা যায়।",
                    "আমার ক্যামেরা রোলের আসল তারকা।",
                    "কিছুই বোঝে না, তবু কী যে আদুরে।",
                    "ওকে আমার হয়ে একটা হাই বলবেন।"
                )
            }
            else -> {
                en = listOf(
                    "Anyway, here’s a photo I really like.",
                    "No special reason. Just liked this one.",
                    "This one felt worth keeping.",
                    "A little moment from lately. ❤️",
                    "I almost didn’t post this, but here we are.",
                    "The kind of day I wouldn’t mind repeating.",
                    "Keeping this one for the memories.",
                    "Not much to say. Happy to have this photo."
                )
                bn = listOf(
                    "যাই হোক, ছবিটা আমার বেশ পছন্দ হয়েছে।",
                    "বিশেষ কোনো কারণ নেই, ছবিটা ভালো লেগেছে।",
                    "এই মুহূর্তটা রেখে দিতে ইচ্ছে হলো।",
                    "ইদানীংকার একটা ছোট্ট মুহূর্ত। ❤️",
                    "পোস্ট করব কি না ভাবছিলাম, শেষমেশ করেই দিলাম।",
                    "এমন একটা দিন আবার পেলে মন্দ হয় না।",
                    "স্মৃতি হিসেবে ছবিটা রেখে দিলাম।",
                    "বেশি কিছু বলার নেই, ছবিটা পেয়ে ভালো লাগছে।"
                )
            }
        }

        // Prefer concise, usable captions; aspect ratio is retained for future framing-aware suggestions.
        @Suppress("UNUSED_VARIABLE")
        val portraitFrame = aspectRatio < 0.8f
        return Suggestions(trusted, en, bn)
    }

    private fun has(text: String, vararg keywords: String): Boolean =
        keywords.any { it in text }
}
