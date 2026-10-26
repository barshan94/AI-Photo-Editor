package com.barshan.aiphotoeditor

/** Pure grouping rules for burst candidates and perceptually similar frame hashes. */
object PhotoGroupingPolicy {
    fun burstGroupIds(captureTimesMillis: List<Long?>, maxSpanMillis: Long = 2_000L): List<Long?> {
        val output = MutableList<Long?>(captureTimesMillis.size) { null }
        val chronological = captureTimesMillis.mapIndexedNotNull { index, time ->
            time?.let { index to it }
        }.sortedBy { it.second }
        var group = mutableListOf<Pair<Int, Long>>()

        fun flush() {
            if (group.size >= 2) {
                val id = group.first().second
                group.forEach { output[it.first] = id }
            }
            group = mutableListOf()
        }

        for (entry in chronological) {
            val first = group.firstOrNull()?.second
            if (first != null && entry.second - first > maxSpanMillis) flush()
            group.add(entry)
        }
        flush()
        return output
    }

    fun isVisuallySimilar(firstHash: Long, secondHash: Long, maxBitDistance: Int = 5): Boolean =
        java.lang.Long.bitCount(firstHash xor secondHash) <= maxBitDistance.coerceAtLeast(0)
}
