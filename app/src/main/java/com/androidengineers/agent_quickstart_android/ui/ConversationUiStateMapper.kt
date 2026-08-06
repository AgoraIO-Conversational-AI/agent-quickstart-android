package com.androidengineers.agent_quickstart_android.ui

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.JournalEntryUiModel
import com.androidengineers.agent_quickstart_android.model.SessionSnapshot
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import io.agora.rtc2.Constants
import java.util.Locale

internal object ConversationUiStateMapper {
    fun freshUiState(
        permissionGranted: Boolean = false,
        warningMessage: String? = null,
        errorMessage: String? = null,
        isDarkTheme: Boolean = false,
        journalEntries: List<JournalEntryUiModel> = emptyList(),
    ): ConversationUiState {
        return ConversationUiState(
            isDarkTheme = isDarkTheme,
            isConfigured = QuickstartConfig.isConfigured,
            configMessage = QuickstartConfig.startupHelpMessage(),
            microphonePermissionGranted = permissionGranted,
            warningMessage = warningMessage,
            errorMessage = errorMessage,
            journalEntries = journalEntries,
        )
    }

    fun mergeSession(
        currentState: ConversationUiState,
        snapshot: SessionSnapshot,
    ): ConversationUiState {
        val (liveList, history) = snapshot.transcriptTurns.partition {
            it.status == TranscriptTurnStatus.IN_PROGRESS
        }
        val liveTranscript = liveList.lastOrNull()
        val visualState = snapshot.toVisualState()

        return currentState.copy(
            channelName = snapshot.channelName,
            localUid = snapshot.localRtcUid.takeIf { it != 0 }?.toString(),
            rtcConnectionLabel = snapshot.rtcConnectionState.toRtcLabel(),
            rtmConnectionLabel = snapshot.rtmConnectionState
                .replace('_', ' ')
                .lowercase(Locale.ROOT)
                .replaceFirstChar { it.titlecase(Locale.ROOT) },
            agentVisualState = visualState,
            agentStateLabel = visualState.toAgentLabel(),
            turnState = snapshot.turnState,
            micEnabled = snapshot.micEnabled,
            micRequestedEnabled = snapshot.micRequestedEnabled,
            micAutoMuted = snapshot.micAutoMuted,
            transcriptHistory = history,
            liveTranscript = liveTranscript,
            issues = snapshot.issues,
            // inConversation is driven entirely by ViewModel (startConversation / freshUiState),
            // never by snapshot fields. startConversation() sets it via current.copy(inConversation=true)
            // before calling mergeSession; endConversation() clears it via freshUiState() before
            // disconnect(), so any collectLatest emission racing after that already reads false here.
            inConversation = currentState.inConversation,
        )
    }

    private fun SessionSnapshot.toVisualState(): AgentVisualState {
        return when {
            rtcConnectionState == Constants.CONNECTION_STATE_DISCONNECTED ||
                rtcConnectionState == Constants.CONNECTION_STATE_FAILED -> AgentVisualState.DISCONNECTED

            rtcConnectionState == Constants.CONNECTION_STATE_CONNECTING ||
                rtcConnectionState == Constants.CONNECTION_STATE_RECONNECTING -> AgentVisualState.WAITING

            !isAgentRtcConnected -> AgentVisualState.WAITING
            agentState == AgentConversationState.LISTENING -> AgentVisualState.LISTENING
            agentState == AgentConversationState.THINKING -> AgentVisualState.THINKING
            agentState == AgentConversationState.SPEAKING -> AgentVisualState.SPEAKING
            else -> AgentVisualState.IDLE
        }
    }

    private fun AgentVisualState.toAgentLabel(): String {
        return when (this) {
            AgentVisualState.WAITING -> "Waiting for the cloud agent"
            AgentVisualState.LISTENING -> "Listening for your turn"
            AgentVisualState.THINKING -> "Thinking through a response"
            AgentVisualState.SPEAKING -> "Speaking back in real time · barge-in ready"
            AgentVisualState.IDLE -> "Connected and ready"
            AgentVisualState.DISCONNECTED -> "Connection interrupted"
        }
    }

    private fun Int.toRtcLabel(): String {
        return when (this) {
            Constants.CONNECTION_STATE_CONNECTED -> "RTC connected"
            Constants.CONNECTION_STATE_CONNECTING -> "RTC connecting"
            Constants.CONNECTION_STATE_RECONNECTING -> "RTC reconnecting"
            Constants.CONNECTION_STATE_FAILED -> "RTC failed"
            else -> "RTC idle"
        }
    }
}
