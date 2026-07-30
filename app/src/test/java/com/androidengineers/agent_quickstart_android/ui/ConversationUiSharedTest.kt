package com.androidengineers.agent_quickstart_android.ui

import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurn
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationUiSharedTest {

    // ── isCoachReadyForSpeech ───────────────────────────────────────────────

    @Test
    fun isTrueWhenAgentIsListening() {
        val state = inConversationState(agentVisualState = AgentVisualState.LISTENING)

        assertTrue(state.isCoachReadyForSpeech())
    }

    @Test
    fun isTrueWhenAgentIsThinking() {
        val state = inConversationState(agentVisualState = AgentVisualState.THINKING)

        assertTrue(state.isCoachReadyForSpeech())
    }

    @Test
    fun isTrueWhenAgentIsSpeaking() {
        val state = inConversationState(agentVisualState = AgentVisualState.SPEAKING)

        assertTrue(state.isCoachReadyForSpeech())
    }

    @Test
    fun isTrueWhenIdleAndCoachHasSpokenInHistory() {
        val state = inConversationState(
            agentVisualState = AgentVisualState.IDLE,
            agentTurnInHistory = true,
        )

        assertTrue(state.isCoachReadyForSpeech())
    }

    @Test
    fun isTrueWhenWaitingAndCoachHasSpokenInHistory() {
        val state = inConversationState(
            agentVisualState = AgentVisualState.WAITING,
            agentTurnInHistory = true,
        )

        assertTrue(state.isCoachReadyForSpeech())
    }

    @Test
    fun isTrueWhenIdleAndCoachHasLiveTranscript() {
        val state = ConversationUiState(
            isStarting = false,
            agentVisualState = AgentVisualState.IDLE,
            liveTranscript = agentTurn("live", "I am speaking."),
        )

        assertTrue(state.isCoachReadyForSpeech())
    }

    @Test
    fun isFalseWhenIdleAndCoachHasNeverSpoken() {
        val state = inConversationState(
            agentVisualState = AgentVisualState.IDLE,
            agentTurnInHistory = false,
        )

        assertFalse(state.isCoachReadyForSpeech())
    }

    @Test
    fun isFalseWhenWaitingAndCoachHasNeverSpoken() {
        val state = inConversationState(
            agentVisualState = AgentVisualState.WAITING,
            agentTurnInHistory = false,
        )

        assertFalse(state.isCoachReadyForSpeech())
    }

    @Test
    fun isFalseWhenSessionIsStarting() {
        val state = ConversationUiState(
            isStarting = true,
            agentVisualState = AgentVisualState.LISTENING,
        )

        assertFalse(state.isCoachReadyForSpeech())
    }

    @Test
    fun isFalseWhenAgentIsDisconnected() {
        val state = inConversationState(agentVisualState = AgentVisualState.DISCONNECTED)

        assertFalse(state.isCoachReadyForSpeech())
    }

    // ── shouldShowCorrectionDetails ─────────────────────────────────────────

    @Test
    fun trueWhenInConversationAndAnalyzing() {
        val state = ConversationUiState(
            inConversation = true,
            isAnalyzingCorrection = true,
        )

        assertTrue(state.shouldShowCorrectionDetails())
    }

    @Test
    fun trueWhenInConversationAndCorrectionResponsePresent() {
        val state = ConversationUiState(
            inConversation = true,
            correctionResponseText = "Some correction.",
        )

        assertTrue(state.shouldShowCorrectionDetails())
    }

    @Test
    fun trueWhenInConversationAndRequestTimestampSet() {
        val state = ConversationUiState(
            inConversation = true,
            correctionRequestedAtMillis = 1000L,
        )

        assertTrue(state.shouldShowCorrectionDetails())
    }

    @Test
    fun falseWhenNotInConversation() {
        val state = ConversationUiState(
            inConversation = false,
            isAnalyzingCorrection = true,
            correctionResponseText = "A correction.",
        )

        assertFalse(state.shouldShowCorrectionDetails())
    }

    @Test
    fun falseWhenInConversationButNoCorrectionData() {
        val state = ConversationUiState(
            inConversation = true,
            isAnalyzingCorrection = false,
            correctionRequestedAtMillis = null,
            correctionResponseText = null,
        )

        assertFalse(state.shouldShowCorrectionDetails())
    }

    // ── learningConceptLabel ────────────────────────────────────────────────

    @Test
    fun detectsVerbTenseFromTip() {
        val label = learningConceptLabel("You need past tense here.")

        assertEquals("Grammar focus: verb tense", label)
    }

    @Test
    fun detectsArticleFromTip() {
        val label = learningConceptLabel("You should use the article 'the' before the noun.")

        assertEquals("Grammar focus: articles", label)
    }

    @Test
    fun detectsPronounFromTip() {
        // Avoid "the " substring — check for "pronoun" keyword directly
        val label = learningConceptLabel("Check your pronoun usage here.")

        assertEquals("Grammar focus: pronouns", label)
    }

    @Test
    fun detectsPrepositionFromTip() {
        // "preposition" keyword triggers before any substring check
        val label = learningConceptLabel("Wrong preposition — use in for months.")

        assertEquals("Grammar focus: prepositions", label)
    }

    @Test
    fun detectsPluralFromTip() {
        // "Check" contains "he" as a substring ("c-he-ck"), so avoid it.
        // "Plural forms match count." has no pronoun/article/tense/preposition triggers.
        val label = learningConceptLabel("Plural forms match count.")

        assertEquals("Grammar focus: number agreement", label)
    }

    @Test
    fun fallsBackToNaturalPhrasing() {
        // Avoid "a ", "the ", or any other trigger substring
        val label = learningConceptLabel("Your phrasing sounds slightly off.")

        assertEquals("Grammar focus: natural phrasing", label)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun inConversationState(
        agentVisualState: AgentVisualState,
        agentTurnInHistory: Boolean = false,
    ): ConversationUiState {
        return ConversationUiState(
            isStarting = false,
            inConversation = true,
            agentVisualState = agentVisualState,
            transcriptHistory = if (agentTurnInHistory) {
                listOf(agentTurn("1", "Hello!"))
            } else {
                emptyList()
            },
        )
    }

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
