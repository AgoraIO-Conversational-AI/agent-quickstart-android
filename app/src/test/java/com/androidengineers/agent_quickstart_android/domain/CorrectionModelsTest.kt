package com.androidengineers.agent_quickstart_android.domain

import com.androidengineers.agent_quickstart_android.domain.correction.CorrectionHighlight
import com.androidengineers.agent_quickstart_android.domain.correction.highlightedCorrectionWords
import com.androidengineers.agent_quickstart_android.domain.correction.inferCorrectionChanges
import com.androidengineers.agent_quickstart_android.domain.correction.parseCorrectionChanges
import com.androidengineers.agent_quickstart_android.domain.correction.parseCorrectionResponse
import com.androidengineers.agent_quickstart_android.domain.correction.toConversationDisplayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionModelsTest {

    // ── parseCorrectionResponse ─────────────────────────────────────────────

    @Test
    fun parsesWellFormedCorrectionBlock() {
        val response = """
            BETTERSAID_CORRECTION
            ORIGINAL: I go to market yesterday and buyed things.
            CORRECTED: I went to the market yesterday and bought things.
            TIP: Use past tense for completed actions.
            CHANGES: go -> went; buyed -> bought
        """.trimIndent()

        val result = parseCorrectionResponse(
            originalFallback = "fallback",
            response = response,
        )

        assertEquals("I go to market yesterday and buyed things.", result.original)
        assertEquals("I went to the market yesterday and bought things.", result.corrected)
        assertTrue(result.hasCorrectedSentence)
        assertTrue(result.tip.contains("past tense"))
        assertTrue(result.changePairs.any { it.from == "go" && it.to == "went" })
        assertTrue(result.changePairs.any { it.from == "buyed" && it.to == "bought" })
    }

    @Test
    fun usesOriginalFallbackWhenOriginalFieldIsMissing() {
        val response = """
            BETTERSAID_CORRECTION
            CORRECTED: I went to the market.
            TIP: Use past tense.
            CHANGES: go -> went
        """.trimIndent()

        val result = parseCorrectionResponse(
            originalFallback = "I go to the market.",
            response = response,
        )

        assertEquals("I go to the market.", result.original)
        assertEquals("I went to the market.", result.corrected)
        assertTrue(result.hasCorrectedSentence)
    }

    @Test
    fun returnsFallbackAsCorrectedWhenResponseIsEmpty() {
        val result = parseCorrectionResponse(
            originalFallback = "She go to school every day.",
            response = "",
        )

        assertFalse(result.hasCorrectedSentence)
        assertEquals("She go to school every day.", result.corrected)
    }

    @Test
    fun handlesNullResponse() {
        val result = parseCorrectionResponse(
            originalFallback = "He buyed a car.",
            response = null,
        )

        assertFalse(result.hasCorrectedSentence)
        assertEquals("He buyed a car.", result.corrected)
    }

    @Test
    fun extractsDefaultTipWhenTipFieldMissing() {
        val response = """
            BETTERSAID_CORRECTION
            ORIGINAL: She go every day.
            CORRECTED: She goes every day.
            CHANGES: go -> goes
        """.trimIndent()

        val result = parseCorrectionResponse("fallback", response)

        assertTrue(result.tip.isNotBlank())
    }

    @Test
    fun coachMessageIsGreetingForCorrectionBlock() {
        // BETTERSAID_CORRECTION blocks suppress the raw content from the coach bubble
        // and instead return a fixed coaching invitation.
        val response = """
            BETTERSAID_CORRECTION
            ORIGINAL: I go market.
            CORRECTED: I went to the market.
            TIP: Use past tense.
            CHANGES: go -> went
        """.trimIndent()

        val result = parseCorrectionResponse("fallback", response)

        assertTrue(result.coachMessage.isNotBlank())
        assertFalse(result.coachMessage.contains("BETTERSAID_CORRECTION"))
        assertFalse(result.coachMessage.contains("ORIGINAL:"))
        assertFalse(result.coachMessage.contains("CORRECTED:"))
    }

    // ── parseCorrectionChanges ──────────────────────────────────────────────

    @Test
    fun parsesArrowSeparatedChangePairs() {
        val changes = "go -> went; buyed -> bought; is eating -> ate"

        val pairs = parseCorrectionChanges(changes)

        assertEquals(3, pairs.size)
        assertEquals("go", pairs[0].from)
        assertEquals("went", pairs[0].to)
        assertEquals("buyed", pairs[1].from)
        assertEquals("bought", pairs[1].to)
        assertEquals("is eating", pairs[2].from)
        assertEquals("ate", pairs[2].to)
    }

    @Test
    fun parsesUnicodArrowSeparator() {
        val changes = "go → went"

        val pairs = parseCorrectionChanges(changes)

        assertEquals(1, pairs.size)
        assertEquals("go", pairs[0].from)
        assertEquals("went", pairs[0].to)
    }

    @Test
    fun parsesDoubleArrowSeparator() {
        val changes = "buyed => bought"

        val pairs = parseCorrectionChanges(changes)

        assertEquals(1, pairs.size)
        assertEquals("buyed", pairs[0].from)
        assertEquals("bought", pairs[0].to)
    }

    @Test
    fun dropsInvalidEntriesWithoutArrow() {
        val changes = "this is not a change; go -> went"

        val pairs = parseCorrectionChanges(changes)

        assertEquals(1, pairs.size)
        assertEquals("go", pairs[0].from)
    }

    @Test
    fun dropsBlankFromOrToAfterTrimming() {
        val changes = "-> went; go ->"

        val pairs = parseCorrectionChanges(changes)

        assertTrue(pairs.isEmpty())
    }

    // ── inferCorrectionChanges ──────────────────────────────────────────────

    @Test
    fun infersSingleWordReplacement() {
        val pairs = inferCorrectionChanges(
            original = "I go to school.",
            corrected = "I went to school.",
        )

        assertTrue(pairs.any { it.from.lowercase() == "go" && it.to.lowercase() == "went" })
    }

    @Test
    fun returnsEmptyForPureArticleInsertionBecauseNoFromWord() {
        // replacementIndexes returns empty when the original-side gap has 0 words;
        // pure insertions ("the" added, nothing removed) produce no change pairs.
        val pairs = inferCorrectionChanges(
            original = "I went to market.",
            corrected = "I went to the market.",
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun returnsEmptyForIdenticalSentences() {
        val pairs = inferCorrectionChanges(
            original = "I went to the market.",
            corrected = "I went to the market.",
        )

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun capsResultAtSixChanges() {
        val original = "a b c d e f g h"
        val corrected = "A B C D E F G H"

        val pairs = inferCorrectionChanges(original = original, corrected = corrected)

        assertTrue(pairs.size <= 6)
    }

    // ── highlightedCorrectionWords ──────────────────────────────────────────

    @Test
    fun marksReplacedWordAsReplacement() {
        val words = highlightedCorrectionWords(
            original = "I go to school.",
            corrected = "I went to school.",
            changePairs = listOf(
                com.androidengineers.agent_quickstart_android.domain.correction.CorrectionChange(
                    from = "go",
                    to = "went",
                )
            ),
        )

        val wenT = words.firstOrNull { it.text.equals("went", ignoreCase = true) }
        assertEquals(CorrectionHighlight.Replacement, wenT?.highlight)
    }

    @Test
    fun leavesUnchangedWordsWithNullHighlight() {
        val words = highlightedCorrectionWords(
            original = "I go to school.",
            corrected = "I went to school.",
            changePairs = listOf(
                com.androidengineers.agent_quickstart_android.domain.correction.CorrectionChange(
                    from = "go",
                    to = "went",
                )
            ),
        )

        val toWord = words.firstOrNull { it.text == "to" }
        assertEquals(null, toWord?.highlight)
    }

    @Test
    fun marksInsertedWordAsInsertion() {
        val words = highlightedCorrectionWords(
            original = "I went market.",
            corrected = "I went to the market.",
            changePairs = emptyList(),
        )

        val inserted = words.filter { it.highlight == CorrectionHighlight.Insertion }
        assertTrue(inserted.isNotEmpty())
    }

    // ── toConversationDisplayText ───────────────────────────────────────────

    @Test
    fun stripsAllStructuredFieldLines() {
        // Any turn containing BETTERSAID_CORRECTION returns empty — the coach bubble
        // is suppressed; the correction details UI renders the structured content.
        val raw = """
            BETTERSAID_CORRECTION
            ORIGINAL: old sentence
            CORRECTED: new sentence
            TIP: a tip
            CHANGES: old -> new
        """.trimIndent()

        val display = raw.toConversationDisplayText()

        assertTrue(display.isEmpty())
    }

    @Test
    fun collapsesMultipleSpaces() {
        val raw = "This   is   spaced  out."

        val display = raw.toConversationDisplayText()

        assertFalse(display.contains("  "))
    }
}
