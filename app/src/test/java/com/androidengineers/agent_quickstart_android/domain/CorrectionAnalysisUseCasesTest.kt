package com.androidengineers.agent_quickstart_android.domain

import com.androidengineers.agent_quickstart_android.domain.conversation.CorrectionAnalysisUseCases
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurn
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionAnalysisUseCasesTest {

    private val useCases = CorrectionAnalysisUseCases()

    // ── userTranscriptForCorrection ─────────────────────────────────────────

    @Test
    fun concatenatesAllCompletedUserTurns() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                userTurn(key = "1", text = "Hello I am testing."),
                agentTurn(key = "2", text = "Good!"),
                userTurn(key = "3", text = "I go market yesterday."),
            ),
        )

        val result = useCases.userTranscriptForCorrection(state)

        assertEquals("Hello I am testing. I go market yesterday.", result)
    }

    @Test
    fun appendsLiveUserTranscriptToHistory() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                userTurn(key = "1", text = "First sentence."),
            ),
            liveTranscript = userTurn(key = "live", text = "Still speaking now."),
        )

        val result = useCases.userTranscriptForCorrection(state)

        assertTrue(result.contains("First sentence."))
        assertTrue(result.contains("Still speaking now."))
    }

    @Test
    fun ignoresAgentTurnsInHistory() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                agentTurn(key = "1", text = "I am the coach."),
                userTurn(key = "2", text = "I go there."),
            ),
        )

        val result = useCases.userTranscriptForCorrection(state)

        assertEquals("I go there.", result)
    }

    @Test
    fun returnsBlankWhenNoUserSpeech() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                agentTurn(key = "1", text = "Hello, how can I help?"),
            ),
        )

        val result = useCases.userTranscriptForCorrection(state)

        assertTrue(result.isBlank())
    }

    @Test
    fun ignoresLiveAgentTranscript() {
        val state = ConversationUiState(
            liveTranscript = agentTurn(key = "live", text = "I am speaking now."),
        )

        val result = useCases.userTranscriptForCorrection(state)

        assertTrue(result.isBlank())
    }

    @Test
    fun collapsesExtraWhitespaceBetweenTurns() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                userTurn(key = "1", text = "  First.  "),
                userTurn(key = "2", text = "  Second.  "),
            ),
        )

        val result = useCases.userTranscriptForCorrection(state)

        assertFalse(result.contains("  "))
    }

    // ── buildAnalysisPrompt ─────────────────────────────────────────────────

    @Test
    fun includesBetterSaidHeaderAndTranscript() {
        val prompt = useCases.buildAnalysisPrompt("I go to market yesterday.")

        assertTrue(prompt.startsWith("BETTERSAID_ANALYZE_TRANSCRIPT"))
        assertTrue(prompt.contains("I go to market yesterday."))
    }

    // ── completedAgentTurnCount ─────────────────────────────────────────────

    @Test
    fun countsOnlyNonBlankAgentTurns() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                agentTurn(key = "1", text = "Hello!"),
                userTurn(key = "2", text = "Hi."),
                agentTurn(key = "3", text = "Good response."),
                agentTurn(key = "4", text = ""),
            ),
        )

        val count = useCases.completedAgentTurnCount(state)

        assertEquals(2, count)
    }

    @Test
    fun returnsZeroWhenNoAgentTurns() {
        val state = ConversationUiState(
            transcriptHistory = listOf(
                userTurn(key = "1", text = "I speak only."),
            ),
        )

        assertEquals(0, useCases.completedAgentTurnCount(state))
    }

    // ── captureCorrectionResponseIfReady ───────────────────────────────────

    @Test
    fun capturesFirstAgentTurnAfterRequestThatHasCorrectedBlock() {
        val correctionBlock = """
            BETTERSAID_CORRECTION
            ORIGINAL: I go to market.
            CORRECTED: I went to the market.
            TIP: Use past tense.
            CHANGES: go -> went
        """.trimIndent()

        val state = ConversationUiState(
            inConversation = true,
            isAnalyzingCorrection = true,
            correctionRequestedAtMillis = 1000L,
            correctionRequestedAgentTurnCount = 1,
            correctionOriginalText = "I go to market.",
            correctionResponseText = null,
            transcriptHistory = listOf(
                agentTurn(key = "1", text = "Hello!"),
                agentTurn(key = "2", text = correctionBlock),
            ),
        )

        val updated = useCases.captureCorrectionResponseIfReady(state)

        assertFalse(updated.isAnalyzingCorrection)
        assertNotNull(updated.correctionResponseText)
        assertEquals("2", updated.correctionAgentTurnKey)
    }

    @Test
    fun doesNotCaptureAgentTurnsBeforeRequestCount() {
        val correctionBlock = """
            BETTERSAID_CORRECTION
            ORIGINAL: I go to market.
            CORRECTED: I went to the market.
            TIP: tip
            CHANGES: go -> went
        """.trimIndent()

        val state = ConversationUiState(
            inConversation = true,
            isAnalyzingCorrection = true,
            correctionRequestedAtMillis = 1000L,
            correctionRequestedAgentTurnCount = 2,
            correctionOriginalText = "I go to market.",
            correctionResponseText = null,
            transcriptHistory = listOf(
                agentTurn(key = "1", text = correctionBlock),
                agentTurn(key = "2", text = "Something else."),
            ),
        )

        val updated = useCases.captureCorrectionResponseIfReady(state)

        assertTrue(updated.isAnalyzingCorrection)
        assertNull(updated.correctionResponseText)
    }

    @Test
    fun returnsStateUnchangedWhenNoRequestPending() {
        val state = ConversationUiState(
            correctionRequestedAtMillis = null,
        )

        val updated = useCases.captureCorrectionResponseIfReady(state)

        assertEquals(state, updated)
    }

    @Test
    fun returnsStateUnchangedWhenResponseAlreadyCaptured() {
        val state = ConversationUiState(
            correctionRequestedAtMillis = 1000L,
            correctionResponseText = "Already captured.",
        )

        val updated = useCases.captureCorrectionResponseIfReady(state)

        assertEquals(state, updated)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun userTurn(key: String, text: String) = TranscriptTurn(
        key = key,
        turnId = key.hashCode().toLong(),
        streamId = null,
        speaker = TranscriptSpeaker.USER,
        text = text,
        status = TranscriptTurnStatus.END,
        createdAtMillis = 0L,
    )

    private fun agentTurn(key: String, text: String) = TranscriptTurn(
        key = key,
        turnId = key.hashCode().toLong(),
        streamId = null,
        speaker = TranscriptSpeaker.AGENT,
        text = text,
        status = TranscriptTurnStatus.END,
        createdAtMillis = 0L,
    )
}
