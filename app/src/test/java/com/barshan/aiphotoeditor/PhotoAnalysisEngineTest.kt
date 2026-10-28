package com.barshan.aiphotoeditor

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAnalysisEngineTest {
    @Test
    fun noDetectedFacesDoesNotAssumeAnEmptyImage() {
        assertEquals("No clear face detected", PhotoAnalysisEngine.summarizeFaceCount(0))
    }

    @Test
    fun oneFaceIsPortraitCandidate() {
        assertEquals("Single-person / portrait candidate", PhotoAnalysisEngine.summarizeFaceCount(1))
    }

    @Test
    fun twoFacesDoNotAssumeThePeopleAreACouple() {
        assertEquals("Two-person candidate; relationship unknown", PhotoAnalysisEngine.summarizeFaceCount(2))
    }

    @Test
    fun threeOrMoreFacesAreTreatedAsGroupCandidates() {
        assertEquals("Group-photo candidate (3 faces)", PhotoAnalysisEngine.summarizeFaceCount(3))
        assertEquals("Group-photo candidate (8 faces)", PhotoAnalysisEngine.summarizeFaceCount(8))
    }
}
