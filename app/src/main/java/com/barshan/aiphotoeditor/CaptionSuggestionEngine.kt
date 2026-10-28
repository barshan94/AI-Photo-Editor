package com.barshan.aiphotoeditor

/**
 * Local bilingual caption writer. Image labels are weak visual evidence, so users can
 * choose a context when the model misses cultural cues such as sindoor or Bijoya.
 */
object CaptionSuggestionEngine {
    data class Suggestions(val detectedLabels: List<String>, val english: List<String>, val bengali: List<String>)

    fun fromLabels(labels: List<Pair<String, Float>>, aspectRatio: Float, theme: String = "Auto"): Suggestions {
        val trusted = labels.filter { it.second >= 0.38f }.sortedByDescending { it.second }
            .map { it.first.trim().lowercase() }.filter { it.isNotBlank() }.distinct().take(8)
        val words = trusted.joinToString(" ")
        val festival = has(words, "sindoor", "sindur", "vermilion", "durga", "bijoya", "dashami", "puja", "worship", "religious festival", "festival", "celebration", "temple", "idol", "goddess", "garland", "sari", "saree")
        val wedding = has(words, "wedding", "bride", "groom", "ceremony", "marriage", "engagement")
        val travel = has(words, "beach", "ocean", "sea", "waterfall", "mountain", "landscape", "sky", "sunset", "sunrise", "nature", "forest", "city", "street", "bridge", "building")
        val people = has(words, "person", "people", "face", "portrait", "human", "man", "woman", "child", "group", "crowd", "family")
        val scene = when {
            theme != "Auto" -> theme
            festival -> "Festival"
            wedding -> "Romantic"
            travel -> "Travel"
            has(words, "food", "dish", "cuisine", "dessert", "fruit") -> "Food"
            has(words, "dog", "cat", "bird", "animal", "pet") -> "Pets"
            has(words, "flower", "plant", "garden") -> "Nature"
            people -> "Family"
            else -> "Aesthetic"
        }
        val en: List<String>
        val bn: List<String>
        when (scene) {
            "Festival" -> {
                en = listOf(
                    "Shubho Bijoya ❤️ Asche bochor abar hobe.",
                    "Sindoor, shankha, smiles—and a heart not ready to say goodbye.",
                    "The puja ends; the memories and blessings come home with us.",
                    "One last pranam, a thousand feelings. Shubho Bijoya.",
                    "Red sindoor, teary eyes, and the promise of another year.",
                    "Bijoya Dashami: bidding farewell, already counting down to next Puja.",
                    "Not just a celebration—a little piece of home, faith and belonging."
                )
                bn = listOf(
                    "শুভ বিজয়া ❤️ আসছে বছর আবার হবে।",
                    "সিঁদুর, শাঁখা, হাসি—বিদায় বলতে মন যে নারাজ।",
                    "পুজো শেষ, কিন্তু আশীর্বাদ আর স্মৃতিগুলো সঙ্গে রয়ে গেল।",
                    "শেষ প্রণাম, হাজারটা অনুভূতি। শুভ বিজয়া।",
                    "লাল সিঁদুর, ভেজা চোখ, আর আগামী বছরের অপেক্ষা।",
                    "বিজয়া দশমী—বিদায় আজ, আবার দেখা হবে আগামী পুজোয়।",
                    "শুধু উৎসব নয়, ঘর-আস্থা-আপনজনের এক টুকরো অনুভূতি।"
                )
            }
            "Romantic" -> {
                en = listOf("My favourite place is wherever this moment has you.", "If I could keep one second, it would be this one.", "Somehow, ordinary days become stories with you.", "A little less posing, a little more us.", "Proof that my camera roll has a favourite person.", "No perfect caption—just my favourite human.", "The kind of memory that smiles back.")
                bn = listOf("আমার প্রিয় জায়গা—যেখানে এই মুহূর্তে তুমি আছো।", "একটা সেকেন্ড রেখে দিতে পারলে, এই মুহূর্তটাই রাখতাম।", "তুমি থাকলে সাধারণ দিনও গল্প হয়ে যায়।", "পোজ কম, আমাদের মতো মুহূর্ত বেশি।", "আমার ক্যামেরা রোলেরও একজন প্রিয় মানুষ আছে।", "নিখুঁত ক্যাপশন নেই—আছে আমার প্রিয় মানুষটা।", "যে স্মৃতি ফিরে তাকালেই হাসায়।")
            }
            "Funny" -> {
                en = listOf("Came for the moment, stayed for 47 retakes.", "The camera caught it before I could act normal.", "Posting this before I change my mind again.", "Main character energy; budget was one phone.", "Proof I do leave the house sometimes.", "One photo, several failed attempts, zero regrets.", "Please admire the photo; the photographer was fighting for their life.")
                bn = listOf("মুহূর্তটা ধরতে এসে ৪৭ বার রিটেক দিলাম।", "আমি স্বাভাবিক হওয়ার আগেই ক্যামেরা ধরে ফেলেছে।", "আবার মত বদলানোর আগেই পোস্ট করে দিলাম।", "মেইন ক্যারেক্টার ভাইব, বাজেট শুধু একটা ফোন।", "মাঝেমধ্যে যে বাড়ি থেকেও বের হই তার প্রমাণ।", "একটা ছবি, অনেক ব্যর্থ চেষ্টা, আফসোস শূন্য।", "ছবিটা দেখুন, ফটোগ্রাফার তখন জীবনযুদ্ধে ব্যস্ত।")
            }
            "Devotional" -> {
                en = listOf("May the blessings stay long after the lights go out.", "Faith in the heart, gratitude in every step.", "A quiet prayer tucked inside a beautiful day.", "May Ma keep our homes filled with courage and kindness.", "Some moments ask for folded hands, not many words.", "Grateful for the blessings we notice—and the ones we don't.", "The ritual ends; the faith travels with us.")
                bn = listOf("আলো নিভে গেলেও আশীর্বাদ যেন থেকে যায়।", "মনে বিশ্বাস, প্রতিটি পদক্ষেপে কৃতজ্ঞতা।", "সুন্দর দিনের ভেতর লুকিয়ে থাকা এক নীরব প্রার্থনা।", "মা আমাদের ঘর সাহস আর মমতায় ভরিয়ে রাখুন।", "কিছু মুহূর্তে কথার চেয়ে জোড়হাতই বেশি মানায়।", "যে আশীর্বাদ দেখি, আর যে আশীর্বাদ বুঝতে পারি না—সব কিছুর জন্য কৃতজ্ঞ।", "আচার শেষ হয়, বিশ্বাস সঙ্গে থেকে যায়।")
            }
            "Travel" -> {
                en = listOf("A little farther from routine, a little closer to myself.", "Took the long way. The view said thank you.", "New streets, fresh air, stories for later.", "Some places make the world feel wonderfully big.", "Collecting moments, not just map pins.", "The kind of detour I would take twice.", "Wish I could bottle this light and bring it home.")
                bn = listOf("রোজকার ব্যস্ততা থেকে একটু দূরে, নিজের আরও একটু কাছে।", "ঘুরপথে এসেছিলাম—দৃশ্যটা ধন্যবাদ জানাল।", "নতুন রাস্তা, খোলা হাওয়া, পরে বলার মতো গল্প।", "কিছু জায়গা পৃথিবীটাকে আরও সুন্দর বড় করে দেখায়।", "শুধু জায়গা নয়, মুহূর্ত জমাচ্ছি।", "এই ঘুরপথে আবারও আসতে রাজি।", "এই আলোটা বোতলে ভরে বাড়ি নিতে ইচ্ছে করে।")
            }
            "Family" -> {
                en = listOf("Different personalities, one familiar kind of love.", "The loudest moments become the stories we keep.", "My people, my kind of chaos.", "A little imperfect, completely ours.", "Years from now, this will mean even more.", "The best part of the frame is who is in it.", "Home is sometimes just everyone in one picture.")
                bn = listOf("আলাদা আলাদা মানুষ, ভালোবাসাটা একই রকম আপন।", "সবচেয়ে কোলাহলের মুহূর্তগুলোই পরে গল্প হয়ে থাকে।", "আমার মানুষ, আমার চেনা বিশৃঙ্খলা।", "একটু অসম্পূর্ণ, পুরোপুরি আমাদের।", "কয়েক বছর পর এই ছবির মানে আরও বেড়ে যাবে।", "ফ্রেমের সেরা অংশ—এর ভেতরের মানুষগুলো।", "কখনও কখনও এক ছবিতে সবাই থাকলেই ঘর পূর্ণ লাগে।")
            }
            "Nature" -> {
                en = listOf("Nothing to prove, nowhere to rush.", "A small reminder that beauty doesn't ask for attention.", "The world was making art before I arrived.", "Paused here because the light got everything right.", "A little green, a little quiet, a better mood.", "Some days need less noise and more sky.", "Nature never needed a filter to make a point.")
                bn = listOf("কিছু প্রমাণ করার নেই, কোথাও তাড়াও নেই।", "সৌন্দর্য যে নজর কাড়তে চায় না, তার ছোট্ট প্রমাণ।", "আমি আসার আগেই পৃথিবী শিল্প তৈরি করছিল।", "আলোটা ঠিকঠাক ছিল বলেই এখানে থেমে গেলাম।", "একটু সবুজ, একটু নীরবতা, মনটা ভালো।", "কিছু দিনে কোলাহল কম, আকাশ বেশি দরকার।", "প্রকৃতির কথা বোঝাতে ফিল্টার লাগে না।")
            }
            "Food" -> {
                en = listOf("A very serious moment of appreciating the menu.", "Some plans are made around the next bite.", "Happiness, served and photographed first.", "Came hungry, left with evidence.", "Worth the wait—and the extra picture.", "Good food, better company, no leftovers promised.", "The camera eats first. Today, it earned it.")
                bn = listOf("মেনুর সৌন্দর্য উপভোগের এক অত্যন্ত গুরুত্বপূর্ণ মুহূর্ত।", "পরের কামড়কে ঘিরেই কিছু পরিকল্পনা হয়।", "আগে ছবি, তারপর আনন্দের প্রথম কামড়।", "খিদে নিয়ে এসেছিলাম, প্রমাণ নিয়ে ফিরলাম।", "অপেক্ষা আর বাড়তি ছবিটা—দুটোরই মূল্য ছিল।", "ভালো খাবার, আরও ভালো সঙ্গ—বেঁচে যাবে কি না বলা যাচ্ছে না।", "ক্যামেরা আগে খায়—আজ তার প্রাপ্য ছিল।")
            }
            "Pets" -> {
                en = listOf("Tiny paws, oversized place in my heart.", "No words, still somehow understands everything.", "The real owner of this camera roll.", "A face that can cancel the worst day.", "Unplanned photo, planned to keep forever.", "Professional at being adorable; amateur at personal space.", "I took the photo. They took over my heart.")
                bn = listOf("ছোট্ট থাবা, হৃদয়ে বিশাল জায়গা।", "কথা নেই, তবু সবকিছু বুঝে যায় যেন।", "এই ক্যামেরা রোলের আসল মালিক।", "যে মুখটা খারাপ দিনও ভালো করে দেয়।", "হঠাৎ তোলা ছবি, চিরকাল রেখে দেওয়ার ইচ্ছে।", "আদুরে হতে পেশাদার, ব্যক্তিগত দূরত্বে অপেশাদার।", "ছবিটা আমি তুলেছি, মনটা ও দখল করেছে।")
            }
            else -> {
                en = listOf("Not a perfect moment. A real one—and that’s better.", "A frame from a day I want to remember properly.", "No big occasion. Still worth keeping.", "Somewhere between the plan and the unexpected.", "The light, the mood, the little details I almost missed.", "A photo for the feeling, not just the feed.", "This looked like a memory before I even took it.")
                bn = listOf("নিখুঁত মুহূর্ত নয়, সত্যিকারের—আর সেটাই সুন্দর।", "একটা দিনের এমন ফ্রেম, যেটা ঠিক করে মনে রাখতে চাই।", "বড় কোনো উপলক্ষ নয়, তবু রেখে দেওয়ার মতো।", "পরিকল্পনা আর অপ্রত্যাশিতের মাঝখানে একটা মুহূর্ত।", "আলো, মেজাজ, আর প্রায় চোখ এড়িয়ে যাওয়া ছোট্ট বিষয়গুলো।", "ফিডের জন্য নয়, অনুভূতিটার জন্য একটা ছবি।", "তোলার আগেই মনে হচ্ছিল, এটা স্মৃতি হয়ে থাকবে।")
            }
        }
        return Suggestions(trusted, en, bn)
    }

    private fun has(text: String, vararg keywords: String): Boolean = keywords.any { it in text }
}