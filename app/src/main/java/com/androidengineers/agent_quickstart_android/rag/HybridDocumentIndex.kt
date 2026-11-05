package com.androidengineers.agent_quickstart_android.rag

import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/** On-device cosine + BM25 rankings fused using reciprocal rank fusion (k=60). */
class HybridDocumentIndex(val passages: List<String>, private val vectors: List<FloatArray>) {
    init { require(passages.size == vectors.size && passages.isNotEmpty()) }
    fun search(query: String, vector: FloatArray): List<Evidence> {
        require(vector.isNotEmpty() && vectors.all { it.size == vector.size })
        val terms = tokens(query).distinct()
        val documents = passages.map(::tokens)
        val average = documents.map { it.size }.average().coerceAtLeast(1.0)
        val frequencies = terms.associateWith { term -> documents.count { term in it } }
        val semantic = passages.indices.sortedByDescending { cosine(vector, vectors[it]) }
        val lexicalScores = documents.map { words ->
            val counts = words.groupingBy { it }.eachCount()
            terms.sumOf { term ->
                val count = counts[term] ?: return@sumOf 0.0
                val df = frequencies.getValue(term)
                ln(1.0 + (passages.size - df + 0.5) / (df + 0.5)) * count * 2.2 /
                    (count + 1.2 * (0.25 + 0.75 * words.size / average))
            }
        }
        val lexical = passages.indices.filter { lexicalScores[it] > 0 }.sortedByDescending { lexicalScores[it] }
        val ranks = semantic.withIndex().associate { it.value to it.index + 1 }
        val keywordRanks = lexical.withIndex().associate { it.value to it.index + 1 }
        return passages.indices.sortedByDescending { index ->
            1.0 / (60 + ranks.getValue(index)) + (keywordRanks[index]?.let { 1.0 / (60 + it) } ?: 0.0)
        }.take(3).map { Evidence(it + 1, passages[it], 0) }
    }

    companion object {
        fun chunks(text: String): List<String> {
            val clean = text.replace(Regex("[ \\t]+"), " ").trim()
            require(clean.isNotBlank()) { "No readable document text found." }
            val chunks = mutableListOf<String>()
            var start = 0
            while (start < clean.length) {
                var end = (start + 700).coerceAtMost(clean.length)
                if (end < clean.length) {
                    val boundary = clean.lastIndexOf(' ', end - 1)
                    if (boundary > start + 400) end = boundary
                }
                chunks += clean.substring(start, end).trim()
                require(chunks.size <= 256) { "Choose a shorter PDF (at most 256 overlapping passages)." }
                if (end == clean.length) break
                start = (end - 100).coerceAtLeast(start + 1)
            }
            return chunks
        }
        private fun tokens(text: String) = Regex("[\\p{L}\\p{N}]+")
            .findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
        private fun cosine(a: FloatArray, b: FloatArray): Double {
            var dot = 0.0; var aa = 0.0; var bb = 0.0
            a.indices.forEach { i -> dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
            return if (aa == 0.0 || bb == 0.0) 0.0 else dot / sqrt(aa * bb)
        }
    }
}
