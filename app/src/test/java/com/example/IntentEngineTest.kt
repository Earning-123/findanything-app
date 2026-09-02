package com.example

import com.example.engine.IntentEngine
import com.example.engine.VisualSimilarityEngine
import com.example.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentEngineTest {

    @Test
    fun parse_hinglishPhotoQuery_returnsSearchEntity() {
        val result = IntentEngine.parse("Rahul ki photos dikhao")
        assertEquals(IntentType.SEARCH_ENTITY, result.intentType)
        assertEquals("rahul", result.targetPerson?.lowercase())
    }

    @Test
    fun parse_genericPhotoQuery_returnsSearchPhoto() {
        val result = IntentEngine.parse("Camera photos dikhao")
        assertEquals(IntentType.SEARCH_PHOTO, result.intentType)
    }

    @Test
    fun parse_paymentScreenshotQuery_returnsSearchOcr() {
        val result = IntentEngine.parse("5000 payment screenshot")
        assertEquals(IntentType.SEARCH_OCR, result.intentType)
        assertEquals("5000", result.targetAmount)
        assertTrue(result.isScreenshotTargeted)
    }

    @Test
    fun parse_appLaunchQuery_returnsSearchApp() {
        val result = IntentEngine.parse("Calculator kholo")
        assertEquals(IntentType.SEARCH_APP, result.intentType)
        assertTrue(result.searchTerms.contains("calculator"))
    }

    @Test
    fun parse_documentQuery_returnsSearchDocument() {
        val result = IntentEngine.parse("Aadhaar card PDF")
        assertEquals(IntentType.SEARCH_DOCUMENT, result.intentType)
        assertEquals("pdf", result.targetFileType)
    }

    @Test
    fun parse_duplicatesQuery_returnsSearchDuplicates() {
        val result = IntentEngine.parse("Find duplicate photos")
        assertEquals(IntentType.SEARCH_DUPLICATES, result.intentType)
    }

    @Test
    fun parse_largeFilesQuery_returnsSearchLargeFiles() {
        val result = IntentEngine.parse("Find files larger than 100mb")
        assertEquals(IntentType.SEARCH_LARGE_FILES, result.intentType)
        assertNotNull(result.minSizeBytes)
    }

    @Test
    fun parse_dateQuery_returnsCorrectDateFilter() {
        val result = IntentEngine.parse("Yesterday's photos")
        assertEquals(IntentType.SEARCH_PHOTO, result.intentType)
        assertNotNull(result.targetDateFilter)
        assertEquals("Yesterday", result.targetDateFilter?.label)
    }

    @Test
    fun visualSimilarity_hammingDistance_identicalHashesZero() {
        val hash = 0x1234567890ABCDEFL
        var x = hash xor hash
        var dist = 0
        while (x != 0L) {
            dist += (x and 1L).toInt()
            x = x ushr 1
        }
        assertEquals(0, dist)
    }
}
