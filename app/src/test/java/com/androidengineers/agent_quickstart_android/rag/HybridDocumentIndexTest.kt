package com.androidengineers.agent_quickstart_android.rag

import org.junit.Assert.*
import org.junit.Test

class HybridDocumentIndexTest {
    @Test fun combinesSemanticAndExactTermSignals() {
        val passages = listOf("Filter maintenance instructions", "The XJ42 indicator means clean the filter", "The battery takes ninety minutes")
        val index = HybridDocumentIndex(passages, listOf(floatArrayOf(1f, 0f), floatArrayOf(0.9f, 0.1f), floatArrayOf(0f, 1f)))
        val result = index.search("XJ42", floatArrayOf(1f, 0f))
        assertEquals(2, result.first().paragraph)
        assertEquals(passages[1], result.first().text)
    }
    @Test fun semanticSearchCanFindPassageWithoutSharedWords() {
        val index = HybridDocumentIndex(listOf("Charge takes ninety minutes", "Wash the filter"), listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
        assertEquals(1, index.search("power replenishment duration", floatArrayOf(1f, 0f)).first().paragraph)
    }
    @Test fun chunkingKeepsOverlapAndBounds() {
        val text = (1..250).joinToString(" ") { "word$it" }
        val chunks = HybridDocumentIndex.chunks(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 700 })
        assertTrue(chunks[0].takeLast(80) in chunks[1])
        assertTrue(chunks.last().endsWith("word250"))
    }
    @Test fun promptLimitsHistoryAndKeepsCompleteEvidence() {
        val prompt = RagPrompt.build("How long to charge?", "x".repeat(10_000), listOf(Evidence(2, "90 minutes", 0)))
        assertTrue(prompt.contains("[S2] 90 minutes"))
        assertTrue(prompt.contains("untrusted reference data"))
        assertTrue(prompt.length < 4000)
    }
}
