package com.androidengineers.agent_quickstart_android.model

import com.androidengineers.agent_quickstart_android.audio.TurnState
import io.agora.rtc2.Constants

enum class TranscriptSpeaker {
    USER,
    AGENT,
}

enum class TranscriptTurnStatus {
    IN_PROGRESS,
    END,
    INTERRUPTED,
}

enum class AgentConversationState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    SILENT,
    UNKNOWN,
}

enum class AgentVisualState {
    WAITING,
    LISTENING,
    THINKING,
    SPEAKING,
    IDLE,
    DISCONNECTED,
}

data class TranscriptTurn(
    val key: String,
    val turnId: Long,
    val streamId: Long?,
    val speaker: TranscriptSpeaker,
    val text: String,
    val status: TranscriptTurnStatus,
    val createdAtMillis: Long,
)

data class SessionIssue(
    val id: String,
    val source: String,
    val code: String,
    val message: String,
    val timestampMillis: Long,
)

data class AgoraTokenBundle(
    val rtcToken: String,
    val rtmToken: String,
    val uid: String,
    val channel: String,
    val rtmUserId: String,
)

data class AgentInviteResult(
    val agentId: String,
    val createTimestampSeconds: Long? = null,
    val state: String? = null,
)

data class RenewalTokens(
    val rtcToken: String,
    val rtmToken: String,
)

data class SessionSnapshot(
    val channelName: String? = null,
    val rtcConnectionState: Int = Constants.CONNECTION_STATE_DISCONNECTED,
    val rtcConnectionReason: Int? = null,
    val rtmConnectionState: String = "DISCONNECTED",
    val rtmLinkState: String = "IDLE",
    val localRtcUid: Int = 0,
    val isAgentRtcConnected: Boolean = false,
    val agentState: AgentConversationState = AgentConversationState.IDLE,
    val turnState: TurnState = TurnState.IDLE,
    val transcriptTurns: List<TranscriptTurn> = emptyList(),
    val micEnabled: Boolean = true,
    val micRequestedEnabled: Boolean = true,
    val micAutoMuted: Boolean = false,
    val audioSourceLabel: String = "uninitialized",
    val aecAvailable: Boolean = false,
    val aecEnabled: Boolean = false,
    val noiseSuppressorEnabled: Boolean = false,
    val ttsQueueSize: Int = 0,
    val lastVadResult: String? = null,
    val lastBargeInEvent: String? = null,
    val issues: List<SessionIssue> = emptyList(),
)

data class ConversationUiState(
    val isConfigured: Boolean = false,
    val configMessage: String? = null,
    val microphonePermissionGranted: Boolean = false,
    val isStarting: Boolean = false,
    val isStopping: Boolean = false,
    val inConversation: Boolean = false,
    val channelName: String? = null,
    val localUid: String? = null,
    val rtcConnectionLabel: String = "Idle",
    val rtmConnectionLabel: String = "Offline",
    val agentVisualState: AgentVisualState = AgentVisualState.WAITING,
    val agentStateLabel: String = "Ready when you are",
    val turnState: TurnState = TurnState.IDLE,
    val micEnabled: Boolean = true,
    val micRequestedEnabled: Boolean = true,
    val micAutoMuted: Boolean = false,
    val transcriptHistory: List<TranscriptTurn> = emptyList(),
    val liveTranscript: TranscriptTurn? = null,
    val warningMessage: String? = null,
    val errorMessage: String? = null,
    val issues: List<SessionIssue> = emptyList(),
)
