package com.androidengineers.agent_quickstart_android.rag

import java.util.Locale

data class Evidence(val paragraph: Int, val text: String, val score: Int)
data class DocumentAnswer(val text: String, val evidence: List<Evidence>)

/** Offline lexical retrieval with verbatim evidence; deliberately does not claim LLM generation. */
class LocalDocumentSearch(text: String) {
    private val chunks = text.split(Regex("\\n\\s*\\n"))
        .flatMap { it.trim().chunked(700) }.filter { it.isNotBlank() }
    val chunkCount: Int get() = chunks.size

    fun answer(question: String): DocumentAnswer {
        val terms = words(question).filterNot { it in stopWords }.toSet()
        val hits = chunks.mapIndexed { index, text ->
            Evidence(index + 1, text, words(text).toSet().intersect(terms).size)
        }.filter { it.score >= minOf(2, terms.size.coerceAtLeast(1)) }
            .sortedByDescending { it.score }.take(3)
        return DocumentAnswer(
            if (hits.isEmpty()) "I couldn't find supporting evidence in this document."
            else hits.first().text,
            hits,
        )
    }

    private fun words(text: String) = Regex("[\\p{L}\\p{N}]+")
        .findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

    companion object {
        private val stopWords = setOf("what", "is", "the", "a", "an", "does", "do", "how", "i", "to", "of", "in", "and", "it", "can", "my", "me", "about", "tell")
        val sample = """
            Field Kit — Indicator guide

            The amber indicator means the filter needs cleaning. Switch off the Field Kit before removing the filter. Rinse the filter with cool water and let it dry completely before reinstalling.

            Field Kit — Battery

            The battery provides up to 6 hours of operation. A full charge takes 90 minutes using the supplied charger.

            Field Kit — Reset

            To reset the Field Kit, hold the blue button for 8 seconds. Resetting clears saved preferences.
        """.trimIndent()
    }
}
