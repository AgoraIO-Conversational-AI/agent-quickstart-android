package com.androidengineers.agent_quickstart_android.domain.correction

import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import java.util.Locale

data class ParsedCorrection(
    val original: String,
    val corrected: String,
    val hasCorrectedSentence: Boolean,
    val tip: String,
    val changes: String,
    val changePairs: List<CorrectionChange>,
    val coachMessage: String,
)

data class CorrectionChange(
    val from: String,
    val to: String,
)

enum class CorrectionHighlight {
    Replacement,
    Insertion,
}

data class CorrectionWord(
    val text: String,
    val highlight: CorrectionHighlight? = null,
)

data class CoachConversationLine(
    val speaker: TranscriptSpeaker,
    val text: String,
)

fun quoteSentence(sentence: String): String {
    val trimmed = sentence.trim()
    return if (trimmed.startsWith("“") || trimmed.startsWith("\"")) {
        trimmed
    } else {
        "“$trimmed”"
    }
}

fun String.toConversationDisplayText(): String {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            line.equals("BETTERSAID_CORRECTION", ignoreCase = true) ||
                line.startsWith("ORIGINAL:", ignoreCase = true) ||
                line.startsWith("CORRECTED:", ignoreCase = true) ||
                line.startsWith("TIP:", ignoreCase = true) ||
                line.startsWith("CHANGES:", ignoreCase = true)
        }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun parseCorrectionResponse(
    originalFallback: String,
    response: String?,
): ParsedCorrection {
    val raw = response.orEmpty()
    fun field(name: String): String {
        val nextFields = listOf("ORIGINAL", "CORRECTED", "TIP", "CHANGES")
            .filterNot { it.equals(name, ignoreCase = true) }
            .joinToString("|")
        return Regex(
            pattern = "(?is)\\b$name\\s*:\\s*(.*?)(?=\\s+\\b(?:$nextFields)\\s*:|$)",
        ).find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    }

    val changes = field("CHANGES")
    val original = field("ORIGINAL").ifBlank { originalFallback }
    val correctedField = field("CORRECTED")
    val extractedCorrected = correctedField.ifBlank {
        raw.extractCorrectedSentenceFallback(originalFallback = originalFallback)
    }
    val corrected = extractedCorrected.ifBlank { originalFallback }
    val hasCorrectedSentence = correctedField.isNotBlank() || extractedCorrected.isNotBlank()

    return ParsedCorrection(
        original = original,
        corrected = corrected,
        hasCorrectedSentence = hasCorrectedSentence,
        tip = field("TIP").ifBlank {
            "BetterSaid will add a specific learning tip once the correction is ready."
        },
        changes = changes,
        changePairs = parseCorrectionChanges(changes).ifEmpty {
            inferCorrectionChanges(original = original, corrected = corrected)
        },
        coachMessage = raw.toCoachMessage(),
    )
}

private fun String.extractCorrectedSentenceFallback(originalFallback: String): String {
    val cleaned = replace("BETTERSAID_CORRECTION", "", ignoreCase = true)
        .replace(Regex("(?is)\\bORIGINAL\\s*:\\s*.*?(?=\\s+\\b(?:CORRECTED|TIP|CHANGES)\\s*:|$)"), "")
        .replace(Regex("(?is)\\bTIP\\s*:\\s*.*?(?=\\s+\\b(?:ORIGINAL|CORRECTED|CHANGES)\\s*:|$)"), "")
        .replace(Regex("(?is)\\bCHANGES\\s*:\\s*.*$"), "")
        .trim()

    val labeledCorrected = Regex(
        pattern = "(?is)(?:corrected sentence|more natural english|natural sentence|better sentence)\\s*(?:is|:)\\s*[\"“]?(.+?)[\"”]?(?:\\.|$)",
    ).find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
    if (labeledCorrected.isNotBlank() &&
        labeledCorrected.normalizeCorrectionToken() != originalFallback.normalizeCorrectionToken()
    ) {
        return labeledCorrected
    }

    val quotedSentence = Regex("[\"“]([^\"”]+)[\"”]").findAll(cleaned)
        .map { it.groupValues[1].trim() }
        .firstOrNull { it.normalizeCorrectionToken() != originalFallback.normalizeCorrectionToken() }
        .orEmpty()
    if (quotedSentence.isNotBlank()) {
        return quotedSentence
    }

    return cleaned
        .takeIf {
            it.isNotBlank() &&
                !it.contains("ORIGINAL:", ignoreCase = true) &&
                !it.contains("TIP:", ignoreCase = true) &&
                it.normalizeCorrectionToken() != originalFallback.normalizeCorrectionToken()
        }
        .orEmpty()
}

private fun String.toCoachMessage(): String {
    val cleaned = lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            line.equals("BETTERSAID_CORRECTION", ignoreCase = true) ||
                line.startsWith("ORIGINAL:", ignoreCase = true) ||
                line.startsWith("CORRECTED:", ignoreCase = true) ||
                line.startsWith("TIP:", ignoreCase = true) ||
                line.startsWith("CHANGES:", ignoreCase = true)
        }
        .joinToString(" ")
        .trim()

    return cleaned.toConversationDisplayText()
}

fun parseCorrectionChanges(changes: String): List<CorrectionChange> {
    return changes
        .split(';', '\n')
        .mapNotNull { part ->
            val pieces = part
                .replace("→", "->")
                .replace("=>", "->")
                .split("->", limit = 2)
            if (pieces.size != 2) {
                null
            } else {
                val from = pieces[0].trim().trim('"', '\'', '`', '-', '*')
                val to = pieces[1].trim().trim('"', '\'', '`', '-', '*')
                if (from.isBlank() || to.isBlank()) null else CorrectionChange(from, to)
            }
        }
}

fun inferCorrectionChanges(
    original: String,
    corrected: String,
): List<CorrectionChange> {
    val originalDisplayWords = original.splitDisplayWords()
    val correctedDisplayWords = corrected.splitDisplayWords()
    val originalWords = originalDisplayWords.map { it.normalizeCorrectionToken() }
    val correctedWords = correctedDisplayWords.map { it.normalizeCorrectionToken() }
    val matches = lcsMatches(originalWords, correctedWords)
    return buildList {
        var previousOriginal = -1
        var previousCorrected = -1
        (matches + (originalWords.size to correctedWords.size)).forEach { (nextOriginal, nextCorrected) ->
            val originalGap = originalDisplayWords.subList(previousOriginal + 1, nextOriginal)
            val correctedGap = correctedDisplayWords.subList(previousCorrected + 1, nextCorrected)
            replacementIndexes(
                originalCount = originalGap.size,
                correctedCount = correctedGap.size,
            ).forEach { (originalGapIndex, correctedGapIndex) ->
                add(
                    CorrectionChange(
                        from = originalGap[originalGapIndex].trimCorrectionDisplay(),
                        to = correctedGap[correctedGapIndex].trimCorrectionDisplay(),
                    )
                )
            }
            previousOriginal = nextOriginal
            previousCorrected = nextCorrected
        }
    }.take(6)
}

private fun replacementIndexes(
    originalCount: Int,
    correctedCount: Int,
): List<Pair<Int, Int>> {
    if (originalCount == 0 || correctedCount == 0) {
        return emptyList()
    }
    if (originalCount == 1 || correctedCount == 1) {
        return listOf(0 to 0)
    }

    return (0 until minOf(originalCount, correctedCount)).map { index ->
        val correctedIndex = if (correctedCount > originalCount) {
            (index * (correctedCount - 1)) / (originalCount - 1)
        } else {
            index
        }
        index to correctedIndex
    }
}

fun highlightedCorrectionWords(
    original: String,
    corrected: String,
    changePairs: List<CorrectionChange>,
): List<CorrectionWord> {
    val correctedDisplayWords = quoteSentence(corrected).splitDisplayWords()
    val originalWords = original.normalizedWords()
    val correctedWords = correctedDisplayWords.map { it.normalizeCorrectionToken() }
    val matchedCorrectedIndices = lcsMatches(originalWords, correctedWords)
        .map { it.second }
        .toSet()
    val unmatchedCorrectedIndices = correctedWords.indices
        .filter { index -> correctedWords[index].isNotBlank() && index !in matchedCorrectedIndices }
        .toSet()
    val inferredReplacementTargets = inferCorrectionChanges(original = original, corrected = corrected)
        .flatMap { it.to.normalizedWords() }
        .filter { it.isNotBlank() }
        .toSet()
    val explicitReplacementTargets = changePairs
        .flatMap { it.to.normalizedWords() }
        .filter { it.isNotBlank() }
        .toSet()
    val replacementTargets = (inferredReplacementTargets + explicitReplacementTargets)
        .filter { target -> correctedWords.any { it == target } }
        .toSet()

    return correctedDisplayWords.mapIndexed { index, word ->
        val normalized = word.normalizeCorrectionToken()
        val highlight = when {
            normalized.isBlank() -> null
            normalized in replacementTargets && index in unmatchedCorrectedIndices -> CorrectionHighlight.Replacement
            index in unmatchedCorrectedIndices -> CorrectionHighlight.Insertion
            else -> null
        }
        CorrectionWord(text = word, highlight = highlight)
    }
}

private fun lcsMatches(
    originalWords: List<String>,
    correctedWords: List<String>,
): List<Pair<Int, Int>> {
    val dp = Array(originalWords.size + 1) { IntArray(correctedWords.size + 1) }
    for (originalIndex in originalWords.indices.reversed()) {
        for (correctedIndex in correctedWords.indices.reversed()) {
            dp[originalIndex][correctedIndex] = if (
                originalWords[originalIndex].isNotBlank() &&
                originalWords[originalIndex] == correctedWords[correctedIndex]
            ) {
                dp[originalIndex + 1][correctedIndex + 1] + 1
            } else {
                maxOf(dp[originalIndex + 1][correctedIndex], dp[originalIndex][correctedIndex + 1])
            }
        }
    }

    return buildList {
        var originalIndex = 0
        var correctedIndex = 0
        while (originalIndex < originalWords.size && correctedIndex < correctedWords.size) {
            if (
                originalWords[originalIndex].isNotBlank() &&
                originalWords[originalIndex] == correctedWords[correctedIndex]
            ) {
                add(originalIndex to correctedIndex)
                originalIndex += 1
                correctedIndex += 1
            } else if (dp[originalIndex + 1][correctedIndex] >= dp[originalIndex][correctedIndex + 1]) {
                originalIndex += 1
            } else {
                correctedIndex += 1
            }
        }
    }
}

private fun String.splitDisplayWords(): List<String> {
    return trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
}

fun String.normalizedWords(): List<String> {
    return splitDisplayWords()
        .mapNotNull { word ->
            word.normalizeCorrectionToken().takeIf { it.isNotBlank() }
        }
}

fun String.normalizeCorrectionToken(): String {
    return trimCorrectionDisplay()
        .lowercase(Locale.ROOT)
}

fun String.trimCorrectionDisplay(): String {
    return trim('“', '”', '"', '\'', ',', '.', '!', '?', ':', ';', '(', ')')
}
