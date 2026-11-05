package com.androidengineers.agent_quickstart_android.rag

import org.junit.Assert.*
import org.junit.Test

class LocalDocumentSearchTest {
    @Test fun retrievesVerbatimEvidence() {
        val result = LocalDocumentSearch(LocalDocumentSearch.sample).answer("What does the amber indicator mean?")
        assertTrue(result.text.contains("filter needs cleaning"))
        assertEquals(result.evidence.first().text, result.text)
    }
    @Test fun refusesUnknownAndEmptyQuestions() {
        val search = LocalDocumentSearch(LocalDocumentSearch.sample)
        assertTrue(search.answer("What is the warranty price?").evidence.isEmpty())
        assertTrue(search.answer("what is the").evidence.isEmpty())
        assertTrue(search.answer("").evidence.isEmpty())
    }
    @Test fun boundsLongPassages() {
        val result = LocalDocumentSearch("amber ".repeat(500)).answer("amber")
        assertTrue(result.evidence.all { it.text.length <= 700 })
        assertTrue(result.evidence.size <= 3)
    }
}
