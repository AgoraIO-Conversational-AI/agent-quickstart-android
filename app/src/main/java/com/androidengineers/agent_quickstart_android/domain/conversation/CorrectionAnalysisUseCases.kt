package com.androidengineers.agent_quickstart_android.domain.conversation

import com.androidengineers.agent_quickstart_android.domain.correction.parseCorrectionResponse
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker

private val RE_MULTI_SPACE = Regex("\\s{2,}")

class CorrectionAnalysisUseCases {
    fun userTranscriptForCorrection(state: ConversationUiState): String {
        val completedTurns = state.transcriptHistory
            .asSequence()
            .filter { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
            .joinToString(" ") { it.text.trim() }
        val liveTurn = state.liveTranscript
            ?.takeIf { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
            ?.text
            ?.trim()
            .orEmpty()

        val joined = if (liveTurn.isBlank()) completedTurns
                     else if (completedTurns.isBlank()) liveTurn
                     else "$completedTurns $liveTurn"
        return joined.replace(RE_MULTI_SPACE, " ").trim()
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
            .asSequence()
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
