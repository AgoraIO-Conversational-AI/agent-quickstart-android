package com.androidengineers.agent_quickstart_android.audio

import kotlin.math.max
import java.util.Locale

data class SelfSpeechDecision(
    val discard: Boolean,
    val reason: String,
    val similarity: Double = 0.0,
)

class SelfSpeechFilter(
    private val interruptCommands: Set<String> = DEFAULT_INTERRUPT_COMMANDS,
) {
    @Volatile
    private var currentAgentText: String = ""

    fun updateCurrentAgentText(text: String) {
        currentAgentText = normalize(text)
    }

    fun clear() {
        currentAgentText = ""
    }

    fun shouldDiscard(asrPartial: String): SelfSpeechDecision {
        val normalizedPartial = normalize(asrPartial)
        val normalizedAgentText = currentAgentText

        if (normalizedPartial.isBlank() || normalizedAgentText.isBlank()) {
            return SelfSpeechDecision(
                discard = false,
                reason = "missing-context",
            )
        }

        if (isProtectedInterrupt(normalizedPartial)) {
            return SelfSpeechDecision(
                discard = false,
                reason = "protected-interrupt-command",
            )
        }

        val partialWords = normalizedPartial.splitToSequence(' ').filter { it.isNotBlank() }.toList()
        val agentWords = normalizedAgentText.splitToSequence(' ').filter { it.isNotBlank() }.toList()
        val containsPhrase = partialWords.size >= 4 && normalizedAgentText.contains(normalizedPartial)
        val wordOverlap = overlapRatio(partialWords, agentWords)
        val prefixSimilarity = prefixSimilarity(partialWords, agentWords)
        val similarity = max(wordOverlap, prefixSimilarity)

        if (containsPhrase) {
            return SelfSpeechDecision(
                discard = true,
                reason = "partial-matches-agent-tts",
                similarity = 1.0,
            )
        }

        return if (partialWords.size >= 4 && similarity >= 0.86) {
            SelfSpeechDecision(
                discard = true,
                reason = "high-similarity-to-agent-tts",
                similarity = similarity,
            )
        } else {
            SelfSpeechDecision(
                discard = false,
                reason = "partial-looks-user-originated",
                similarity = similarity,
            )
        }
    }

    private fun isProtectedInterrupt(normalizedPartial: String): Boolean {
        return interruptCommands.any { command ->
            normalizedPartial == command || normalizedPartial.startsWith("$command ")
        }
    }

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace(RE_NON_ALPHANUM, " ")
            .replace(RE_SPACES, " ")
            .trim()
    }

    private fun overlapRatio(
        partialWords: List<String>,
        agentWords: List<String>,
    ): Double {
        if (partialWords.isEmpty() || agentWords.isEmpty()) {
            return 0.0
        }
        val agentWordSet = agentWords.toSet()
        val matched = partialWords.count(agentWordSet::contains)
        return matched.toDouble() / partialWords.size.toDouble()
    }

    private fun prefixSimilarity(
        partialWords: List<String>,
        agentWords: List<String>,
    ): Double {
        if (partialWords.isEmpty() || agentWords.isEmpty()) {
            return 0.0
        }

        var best = 0.0
        val windowSize = partialWords.size
        for (startIndex in 0..(agentWords.size - windowSize).coerceAtLeast(0)) {
            val endIndex = (startIndex + windowSize).coerceAtMost(agentWords.size)
            val window = agentWords.subList(startIndex, endIndex)
            if (window.isEmpty()) continue
            var exactMatches = 0
            for (i in window.indices) {
                if (partialWords[i] == window[i]) exactMatches++
            }
            best = max(best, exactMatches.toDouble() / partialWords.size.toDouble())
        }
        return best
    }

    companion object {
        val DEFAULT_INTERRUPT_COMMANDS: Set<String> = setOf(
            "stop",
            "wait",
            "no",
            "hold on",
            "repeat that",
            "i meant tomorrow",
        )
        private val RE_NON_ALPHANUM = Regex("[^a-z0-9\\s]")
        private val RE_SPACES = Regex("\\s+")
    }
}
