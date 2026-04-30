package com.androidengineers.agent_quickstart_android.audio

import kotlin.math.max

data class SelfSpeechDecision(
    val discard: Boolean,
    val reason: String,
    val similarity: Double = 0.0,
)

class SelfSpeechFilter(
    private val interruptCommands: Set<String> = DEFAULT_INTERRUPT_COMMANDS,
) {
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

        val partialWords = normalizedPartial.split(' ').filter { it.isNotBlank() }
        val agentWords = normalizedAgentText.split(' ').filter { it.isNotBlank() }
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
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
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
            val window = agentWords.drop(startIndex).take(windowSize)
            if (window.isEmpty()) {
                continue
            }
            val exactMatches = partialWords.zip(window).count { (left, right) -> left == right }
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
    }
}
