package com.barshan.aiphotoeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSuggestionEngineTest {
    @Test
    fun durgaAndSindoorLabelsSuggestBijoyaCaptions() {
        val result = CaptionSuggestionEngine.fromLabels(
            listOf("durga" to 0.96f, "sindoor" to 0.89f), 1f
        )
        assertTrue(result.english.any { it.contains("Shubho Bijoya") })
        assertTrue(result.bengali.any { it.contains("শুভ বিজয়া") })
    }

    @Test
    fun sareeAloneDoesNotAssumeThePhotoIsDurgaPuja() {
        val result = CaptionSuggestionEngine.fromLabels(listOf("saree" to 0.99f), 0.7f)
        assertFalse(result.english.any { it.contains("Bijoya") || it.contains("Puja") })
    }

    @Test
    fun explicitDurgaPujaContextWorksEvenWhenImageLabelsMissTheCulture() {
        val result = CaptionSuggestionEngine.fromLabels(
            listOf("woman" to 0.95f, "red clothing" to 0.80f), 0.9f, "Durga Puja"
        )
        assertTrue(result.english.any { it.contains("Shubho Bijoya") })
        assertTrue(result.bengali.any { it.contains("শুভ বিজয়া") })
    }

    @Test
    fun genericFestivalLabelsDoNotAssumeDurgaBijoya() {
        val result = CaptionSuggestionEngine.fromLabels(listOf("festival" to 0.95f), 1f)
        assertFalse(result.english.any { it.contains("Bijoya") || it.contains("Durga") })
    }

    @Test
    fun manuallyChoosingFestivalDoesNotForceBijoyaWithoutSpecificClues() {
        val result = CaptionSuggestionEngine.fromLabels(
            listOf("festival" to 0.95f), 1f, theme = "Festival"
        )
        assertTrue(result.english.any { it.contains("tradition") })
        assertFalse(result.english.any { it.contains("Bijoya") || it.contains("Durga") })
    }

    @Test
    fun genericGroupDoesNotAssumeThePeopleAreFriends() {
        val result = CaptionSuggestionEngine.fromLabels(listOf("group" to 0.92f), 1f)
        assertFalse(result.english.any { it.contains("Same idiots") })
    }

    @Test
    fun banglishThemeIncludesNaturalRomanizedBengali() {
        val result = CaptionSuggestionEngine.fromLabels(emptyList(), 1f, theme = "Banglish")
        assertTrue(result.english.any { it.contains("Shobai mile") })
        assertTrue(result.bengali.isNotEmpty())
    }

    @Test
    fun singlePersonGetsCasualPortraitCaptionsInsteadOfFamilyCaptions() {
        val result = CaptionSuggestionEngine.fromLabels(listOf("person" to 0.95f), 0.8f)
        assertTrue(result.english.any { it.contains("Felt cute") })
        assertFalse(result.english.any { it.contains("family album") })
    }

    @Test
    fun groupOfFriendsGetsFriendStyleCaptions() {
        val result = CaptionSuggestionEngine.fromLabels(
            listOf("friends" to 0.94f, "group" to 0.91f), 1.2f
        )
        assertTrue(result.english.any { it.contains("Same idiots") })
    }
}
