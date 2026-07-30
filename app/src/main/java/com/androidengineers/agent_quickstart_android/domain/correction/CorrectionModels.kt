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

private val RE_MULTI_SPACE = Regex("\\s+")

fun String.toConversationDisplayText(): String {
    // If the turn is a BETTERSAID_CORRECTION block, suppress it entirely — the correction
    // details UI renders it; showing it again in the coach bubble is always wrong.
    if (contains("BETTERSAID_CORRECTION", ignoreCase = true)) {
        return ""
    }
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            line.startsWith("ORIGINAL:", ignoreCase = true) ||
                line.startsWith("CORRECTED:", ignoreCase = true) ||
                line.startsWith("TIP:", ignoreCase = true) ||
                line.startsWith("CHANGES:", ignoreCase = true)
        }
        .joinToString(" ")
        .replace(RE_MULTI_SPACE, " ")
        .trim()
}

internal val RE_FIELD_ORIGINAL = Regex("(?is)\\bORIGINAL\\s*:\\s*(.*?)(?=\\s+\\b(?:CORRECTED|TIP|CHANGES)\\s*:|$)")
internal val RE_FIELD_CORRECTED = Regex("(?is)\\bCORRECTED\\s*:\\s*(.*?)(?=\\s+\\b(?:ORIGINAL|TIP|CHANGES)\\s*:|$)")
internal val RE_FIELD_TIP = Regex("(?is)\\bTIP\\s*:\\s*(.*?)(?=\\s+\\b(?:ORIGINAL|CORRECTED|CHANGES)\\s*:|$)")
internal val RE_FIELD_CHANGES = Regex("(?is)\\bCHANGES\\s*:\\s*(.*?)(?=\\s+\\b(?:ORIGINAL|CORRECTED|TIP)\\s*:|$)")

fun parseCorrectionResponse(
    originalFallback: String,
    response: String?,
): ParsedCorrection {
    val raw = response.orEmpty()
    fun field(regex: Regex): String = regex.find(raw)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    val changes = field(RE_FIELD_CHANGES)
    val original = field(RE_FIELD_ORIGINAL).ifBlank { originalFallback }
    val correctedField = field(RE_FIELD_CORRECTED)
    val extractedCorrected = correctedField.ifBlank {
        raw.extractCorrectedSentenceFallback(originalFallback = originalFallback)
    }
    val corrected = extractedCorrected.ifBlank { originalFallback }
    val hasCorrectedSentence = correctedField.isNotBlank() || extractedCorrected.isNotBlank()

    return ParsedCorrection(
        original = original,
        corrected = corrected,
        hasCorrectedSentence = hasCorrectedSentence,
        tip = field(RE_FIELD_TIP).ifBlank {
            "BetterSaid will add a specific learning tip once the correction is ready."
        },
        changes = changes,
        changePairs = parseCorrectionChanges(changes).ifEmpty {
            inferCorrectionChanges(original = original, corrected = corrected)
        },
        coachMessage = if (raw.contains("BETTERSAID_CORRECTION", ignoreCase = true)) {
            "I've adjusted your sentence — ask me why a word changed, how to practice it, or try another sentence!"
        } else {
            raw.toConversationDisplayText()
        },
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
                // Stop at a lowercase→UPPERCASE junction: the agent sometimes appends the
                // spoken confirmation sentence directly to the last change with no separator
                // (e.g. "to schoolI will go to school tomorrow."). Splitting at that boundary
                // recovers the real change value ("to school") and discards the sentence.
                val rawTo = pieces[1].trim().trim('"', '\'', '`', '-', '*')
                val to = rawTo.split(Regex("(?<=[a-z])(?=[A-Z])")).first().trim()
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
    return inferCorrectionChangesFromLcs(
        originalDisplayWords = originalDisplayWords,
        correctedDisplayWords = correctedDisplayWords,
        matches = lcsMatches(originalWords, correctedWords),
    )
}

private fun inferCorrectionChangesFromLcs(
    originalDisplayWords: List<String>,
    correctedDisplayWords: List<String>,
    matches: List<Pair<Int, Int>>,
): List<CorrectionChange> {
    return buildList {
        var previousOriginal = -1
        var previousCorrected = -1
        (matches + (originalDisplayWords.size to correctedDisplayWords.size)).forEach { (nextOriginal, nextCorrected) ->
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
    val originalDisplayWords = original.splitDisplayWords()
    val originalWords = originalDisplayWords.map { it.normalizeCorrectionToken() }
    val correctedWords = correctedDisplayWords.map { it.normalizeCorrectionToken() }
    val correctedWordSet = correctedWords.toHashSet()

    // Compute LCS once and reuse for both matched-index calculation and inferred changes.
    val lcs = lcsMatches(originalWords, correctedWords)
    val matchedCorrectedIndices = lcs.mapTo(HashSet()) { it.second }
    val unmatchedCorrectedIndices = correctedWords.indices
        .filterTo(HashSet()) { index -> correctedWords[index].isNotBlank() && index !in matchedCorrectedIndices }

    // Derive inferred changes from the already-computed LCS instead of re-running it.
    val inferredReplacementTargets = inferCorrectionChangesFromLcs(
        originalDisplayWords = originalDisplayWords,
        correctedDisplayWords = correctedDisplayWords,
        matches = lcs,
    ).asSequence().flatMap { it.to.normalizedWords() }.filterTo(HashSet()) { it.isNotBlank() }
    val explicitReplacementTargets = changePairs
        .asSequence().flatMap { it.to.normalizedWords() }.filterTo(HashSet()) { it.isNotBlank() }
    val replacementTargets = (inferredReplacementTargets + explicitReplacementTargets)
        .filterTo(HashSet()) { it in correctedWordSet }

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
