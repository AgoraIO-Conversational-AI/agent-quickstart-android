package com.androidengineers.agent_quickstart_android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.data.ConversationRepository
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.SessionSnapshot
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import com.androidengineers.agent_quickstart_android.rtc.AgoraConversationSessionManager
import io.agora.rtc2.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ConversationRepository()
    private val sessionManager = AgoraConversationSessionManager(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(freshUiState())

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeAgentId: String? = null

    init {
        scope.launch {
            sessionManager.snapshot.collectLatest { snapshot ->
                _uiState.update { current ->
                    current.withSession(snapshot)
                }
            }
        }
    }

    fun updateMicrophonePermission(granted: Boolean) {
        _uiState.update { it.copy(microphonePermissionGranted = granted) }
    }

    fun startConversation() {
        val currentState = _uiState.value
        if (currentState.isStarting || currentState.isStopping) {
            return
        }
        if (!QuickstartConfig.isConfigured) {
            _uiState.update {
                it.copy(
                    errorMessage = configurationHelpMessage(),
                    warningMessage = null,
                )
            }
            return
        }
        if (!currentState.microphonePermissionGranted) {
            _uiState.update {
                it.copy(
                    errorMessage = "Microphone access is required to publish your voice to the Agora channel.",
                    warningMessage = null,
                )
            }
            return
        }

        scope.launch {
            _uiState.update {
                it.copy(
                    isStarting = true,
                    errorMessage = null,
                    warningMessage = null,
                )
            }

            runCatching {
                val bootstrap = repository.requestSessionBootstrap()
                val inviteJob = async {
                    runCatching {
                        repository.inviteAgent(channelName = bootstrap.channel)
                    }.getOrNull()
                }

                sessionManager.connect(bootstrap) { channel, rtcUid, rtmUserId ->
                    repository.renewTokens(
                        channel = channel,
                        rtcUid = rtcUid,
                        rtmUserId = rtmUserId,
                    )
                }

                val inviteResult = inviteJob.await()
                activeAgentId = inviteResult?.agentId
                sessionManager.setActiveAgentId(activeAgentId)

                val warning = if (inviteResult?.agentId == null) {
                    "The Android client joined the channel, but the direct Agora REST agent start did not succeed. Verify AGORA_APP_CERTIFICATE and your Agora project settings."
                } else {
                    null
                }

                _uiState.update { current ->
                    current.copy(
                        isStarting = false,
                        inConversation = true,
                        warningMessage = warning,
                    ).withSession(sessionManager.snapshot.value)
                }
            }.onFailure { error ->
                sessionManager.disconnect(resetSnapshot = true)
                activeAgentId = null
                _uiState.value = freshUiState(
                    permissionGranted = _uiState.value.microphonePermissionGranted,
                    errorMessage = error.message ?: "Unable to start the Agora conversation.",
                )
            }
        }
    }

    fun endConversation() {
        val currentState = _uiState.value
        if (currentState.isStopping || currentState.isStarting) {
            return
        }

        scope.launch {
            _uiState.update { it.copy(isStopping = true) }

            val warning = activeAgentId?.let { agentId ->
                val channelName = sessionManager.snapshot.value.channelName
                runCatching {
                    if (channelName != null) {
                        repository.stopConversation(agentId, channelName)
                    }
                }.exceptionOrNull()?.message?.let { message ->
                    "The local session ended, but the direct Agora REST leave request failed: $message"
                }
            }

            activeAgentId = null
            sessionManager.setActiveAgentId(null)
            sessionManager.disconnect(resetSnapshot = true)
            _uiState.value = freshUiState(
                permissionGranted = _uiState.value.microphonePermissionGranted,
                warningMessage = warning,
            )
        }
    }

    fun toggleMicrophone() {
        sessionManager.setMicrophoneEnabled(!_uiState.value.micRequestedEnabled)
    }

    fun clearTransientMessages() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                warningMessage = null,
            )
        }
    }

    override fun onCleared() {
        sessionManager.release()
        super.onCleared()
    }

    private fun freshUiState(
        permissionGranted: Boolean = false,
        warningMessage: String? = null,
        errorMessage: String? = null,
    ): ConversationUiState {
        return ConversationUiState(
            isConfigured = QuickstartConfig.isConfigured,
            configMessage = configurationHelpMessage(),
            microphonePermissionGranted = permissionGranted,
            warningMessage = warningMessage,
            errorMessage = errorMessage,
        )
    }

    private fun configurationHelpMessage(): String? {
        val missing = QuickstartConfig.missingRequiredValues()
        if (missing.isEmpty()) {
            return null
        }
        return "Add ${missing.joinToString()} to local.properties before starting the Android quickstart."
    }

    private fun ConversationUiState.withSession(snapshot: SessionSnapshot): ConversationUiState {
        val liveTranscript = snapshot.transcriptTurns.lastOrNull {
            it.status == TranscriptTurnStatus.IN_PROGRESS
        }
        val history = snapshot.transcriptTurns.filter {
            it.status != TranscriptTurnStatus.IN_PROGRESS
        }

        return copy(
            channelName = snapshot.channelName,
            localUid = snapshot.localRtcUid.takeIf { it != 0 }?.toString(),
            rtcConnectionLabel = snapshot.rtcConnectionState.toRtcLabel(),
            rtmConnectionLabel = snapshot.rtmConnectionState.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() },
            agentVisualState = snapshot.toVisualState(),
            agentStateLabel = snapshot.toAgentLabel(),
            turnState = snapshot.turnState,
            micEnabled = snapshot.micEnabled,
            micRequestedEnabled = snapshot.micRequestedEnabled,
            micAutoMuted = snapshot.micAutoMuted,
            transcriptHistory = history,
            liveTranscript = liveTranscript,
            issues = snapshot.issues,
            inConversation = inConversation || snapshot.channelName != null,
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
