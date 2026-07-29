package com.androidengineers.agent_quickstart_android.domain.conversation

import com.androidengineers.agent_quickstart_android.domain.correction.parseCorrectionResponse
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker

class CorrectionAnalysisUseCases {
    fun userTranscriptForCorrection(state: ConversationUiState): String {
        val completedTurns = state.transcriptHistory
            .filter { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
            .joinToString(" ") { it.text.trim() }
        val liveTurn = state.liveTranscript
            ?.takeIf { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
            ?.text
            ?.trim()
            .orEmpty()

        return listOf(completedTurns, liveTurn)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    fun buildAnalysisPrompt(transcript: String): String {
        return """
BETTERSAID_ANALYZE_TRANSCRIPT

Learner transcript:
$transcript
        """.trimIndent()
    }

    fun completedAgentTurnCount(state: ConversationUiState): Int {
        return state.transcriptHistory.count {
            it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank()
        }
    }

    fun captureCorrectionResponseIfReady(state: ConversationUiState): ConversationUiState {
        state.correctionRequestedAtMillis ?: return state
        if (state.correctionResponseText != null) {
            return state
        }

        val correctionTurn = state.transcriptHistory
            .filter { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
            .drop(state.correctionRequestedAgentTurnCount)
            .firstOrNull { turn ->
                parseCorrectionResponse(
                    originalFallback = state.correctionOriginalText.orEmpty(),
                    response = turn.text,
                ).hasCorrectedSentence
            }

        return if (correctionTurn == null) {
            state
        } else {
            state.copy(
                isAnalyzingCorrection = false,
                correctionResponseText = correctionTurn.text,
                correctionAgentTurnKey = correctionTurn.key,
            )
        }
    }
}
