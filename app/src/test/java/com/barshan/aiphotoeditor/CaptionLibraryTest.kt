package com.barshan.aiphotoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionLibraryTest {
    @Test
    fun shipsAtLeastOneThousandUniqueOfflineCaptions() {
        assertTrue(CaptionLibrary.all.size >= 1000)
        assertEquals(CaptionLibrary.all.size, CaptionLibrary.all.map { it.text }.distinct().size)
    }

    @Test
    fun categoryAndTextSearchReturnRelevantCaptions() {
        val festival = CaptionLibrary.search("festival", category = "Festival")
        assertTrue(festival.isNotEmpty())
        assertTrue(festival.all { it.category == "Festival" })

        val travel = CaptionLibrary.search("journey", category = "Travel")
        assertTrue(travel.isNotEmpty())
        assertTrue(travel.any { it.text.contains("journey", ignoreCase = true) })
    }

    @Test
    fun blankSearchCanBeLimitedAndCategoryFiltered() {
        val results = CaptionLibrary.search("", category = "Portrait", limit = 5)
        assertEquals(5, results.size)
        assertFalse(results.any { it.category != "Portrait" })
    }
}
