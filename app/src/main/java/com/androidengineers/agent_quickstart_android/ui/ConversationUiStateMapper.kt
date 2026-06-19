package com.androidengineers.agent_quickstart_android.ui

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.SessionSnapshot
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import io.agora.rtc2.Constants
import java.util.Locale

internal object ConversationUiStateMapper {
    fun freshUiState(
        permissionGranted: Boolean = false,
        warningMessage: String? = null,
        errorMessage: String? = null,
    ): ConversationUiState {
        return ConversationUiState(
            isConfigured = QuickstartConfig.isConfigured,
            configMessage = QuickstartConfig.startupHelpMessage(),
            microphonePermissionGranted = permissionGranted,
            warningMessage = warningMessage,
            errorMessage = errorMessage,
        )
    }

    fun mergeSession(
        currentState: ConversationUiState,
        snapshot: SessionSnapshot,
    ): ConversationUiState {
        val liveTranscript = snapshot.transcriptTurns.lastOrNull {
            it.status == TranscriptTurnStatus.IN_PROGRESS
        }
        val history = snapshot.transcriptTurns.filter {
            it.status != TranscriptTurnStatus.IN_PROGRESS
        }

        return currentState.copy(
            channelName = snapshot.channelName,
            localUid = snapshot.localRtcUid.takeIf { it != 0 }?.toString(),
            rtcConnectionLabel = snapshot.rtcConnectionState.toRtcLabel(),
            rtmConnectionLabel = snapshot.rtmConnectionState
                .replace('_', ' ')
                .lowercase(Locale.ROOT)
                .replaceFirstChar { it.titlecase(Locale.ROOT) },
            agentVisualState = snapshot.toVisualState(),
            agentStateLabel = snapshot.toAgentLabel(),
            turnState = snapshot.turnState,
            micEnabled = snapshot.micEnabled,
            micRequestedEnabled = snapshot.micRequestedEnabled,
            micAutoMuted = snapshot.micAutoMuted,
            transcriptHistory = history,
            liveTranscript = liveTranscript,
            issues = snapshot.issues,
            inConversation = currentState.inConversation || snapshot.channelName != null,
        )
    }

    private fun SessionSnapshot.toVisualState(): AgentVisualState {
        return when {
            rtcConnectionState == Constants.CONNECTION_STATE_DISCONNECTED ||
                rtcConnectionState == Constants.CONNECTION_STATE_FAILED -> AgentVisualState.DISCONNECTED

            rtcConnectionState == Constants.CONNECTION_STATE_CONNECTING ||
                rtcConnectionState == Constants.CONNECTION_STATE_RECONNECTING -> AgentVisualState.WAITING

            !isAgentRtcConnected -> AgentVisualState.WAITING
            agentState.name == "LISTENING" -> AgentVisualState.LISTENING
            agentState.name == "THINKING" -> AgentVisualState.THINKING
            agentState.name == "SPEAKING" -> AgentVisualState.SPEAKING
            else -> AgentVisualState.IDLE
        }
    }

    private fun SessionSnapshot.toAgentLabel(): String {
        return when (toVisualState()) {
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
