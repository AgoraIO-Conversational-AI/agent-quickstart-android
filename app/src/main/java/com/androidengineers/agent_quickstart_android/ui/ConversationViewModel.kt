package com.androidengineers.agent_quickstart_android.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.data.ConversationRepository
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.rtc.AgoraConversationSessionManager
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
    private val _uiState = MutableStateFlow(ConversationUiStateMapper.freshUiState())

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeAgentId: String? = null
    private var themeInitialized: Boolean = false

    init {
        viewModelScope.launch {
            sessionManager.snapshot.collectLatest { snapshot ->
                _uiState.update { current ->
                    ConversationUiStateMapper.mergeSession(current, snapshot)
                }
            }
        }
    }

    fun updateMicrophonePermission(granted: Boolean) {
        _uiState.update { it.copy(microphonePermissionGranted = granted) }
    }

    fun initializeTheme(systemDarkTheme: Boolean) {
        if (themeInitialized) {
            return
        }
        themeInitialized = true
        _uiState.update { it.copy(isDarkTheme = systemDarkTheme) }
    }

    fun toggleTheme() {
        themeInitialized = true
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun startConversation() {
        val currentState = _uiState.value
        if (currentState.isStarting || currentState.isStopping) {
            return
        }
        if (!QuickstartConfig.isConfigured) {
            _uiState.update {
                it.copy(
                    errorMessage = QuickstartConfig.startupHelpMessage(),
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
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStarting = true,
                    errorMessage = null,
                    warningMessage = null,
                )
            }

            runCatching {
                val healthStartedAt = SystemClock.elapsedRealtime()
                val health = repository.checkHealth()
                _uiState.update {
                    it.copy(
                        backendLatencyMs = SystemClock.elapsedRealtime() - healthStartedAt,
                        lastServerResponse = "${health.status} (${health.version})",
                    )
                }
                val bootstrap = repository.requestSessionBootstrap()
                sessionManager.connect(bootstrap) { channel, rtcUid, rtmUserId ->
                    repository.renewTokens(
                        channel = channel,
                        rtcUid = rtcUid,
                        rtmUserId = rtmUserId,
                    )
                }

                val requesterRtcUid = sessionManager.snapshot.value.localRtcUid
                    .takeIf { it > 0 }
                    ?.toString()
                    ?: bootstrap.uid
                val inviteAttempt = runCatching {
                    repository.inviteAgent(
                        channelName = bootstrap.channel,
                        requesterRtcUid = requesterRtcUid,
                    )
                }
                val inviteResult = inviteAttempt.getOrNull()
                activeAgentId = inviteResult?.agentId
                sessionManager.setActiveAgentId(activeAgentId)

                val warning = inviteAttempt.exceptionOrNull()?.message?.let { message ->
                    "The Android client joined the channel, but the server could not start the Agora agent: $message"
                } ?: if (inviteResult?.agentId == null) {
                    "The Android client joined the channel, but the server returned no Agora agent ID."
                } else null

                _uiState.update { current ->
                    ConversationUiStateMapper.mergeSession(
                        current.copy(
                            isStarting = false,
                            inConversation = true,
                            canSendText = activeAgentId != null,
                            warningMessage = warning,
                        ),
                        sessionManager.snapshot.value,
                    )
                }
            }.onFailure { error ->
                sessionManager.disconnect(resetSnapshot = true)
                activeAgentId = null
                _uiState.value = ConversationUiStateMapper.freshUiState(
                    permissionGranted = _uiState.value.microphonePermissionGranted,
                    errorMessage = error.message ?: "Unable to start the conversation through the quickstart server.",
                    isDarkTheme = _uiState.value.isDarkTheme,
                ).copy(lastServerResponse = "Request failed")
            }
        }
    }

    fun endConversation() {
        val currentState = _uiState.value
        if (currentState.isStopping || currentState.isStarting) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isStopping = true) }

            val warning = activeAgentId?.let { agentId ->
                val channelName = sessionManager.snapshot.value.channelName
                runCatching {
                    if (channelName != null) {
                        repository.stopConversation(agentId, channelName)
                    }
                }.exceptionOrNull()?.message?.let { message ->
                    "The local session ended, but the server leave request failed: $message"
                }
            }

            activeAgentId = null
            sessionManager.setActiveAgentId(null)
            sessionManager.disconnect(resetSnapshot = true)
            _uiState.value = ConversationUiStateMapper.freshUiState(
                permissionGranted = _uiState.value.microphonePermissionGranted,
                warningMessage = warning,
                isDarkTheme = _uiState.value.isDarkTheme,
            )
        }
    }

    fun toggleMicrophone() {
        sessionManager.setMicrophoneEnabled(!_uiState.value.micRequestedEnabled)
    }

    fun updateTextDraft(text: String) {
        _uiState.update { it.copy(textDraft = text.take(2000), textActionStatus = null) }
    }

    fun sendText(speak: Boolean, append: Boolean) {
        val state = _uiState.value
        val agentId = activeAgentId ?: return
        val channel = sessionManager.snapshot.value.channelName ?: return
        val text = state.textDraft.trim()
        if (text.isEmpty() || state.isSendingText || state.isStopping || !state.inConversation) return
        _uiState.update { it.copy(isSendingText = true, textActionStatus = null) }
        viewModelScope.launch {
            val result = runCatching { repository.sendText(agentId, channel, text, speak, append) }
            // A reply from an ended session must not modify a newly started one.
            if (activeAgentId != agentId) return@launch
            _uiState.update {
                it.copy(
                    isSendingText = false,
                    textDraft = if (result.isSuccess && it.textDraft == state.textDraft) "" else it.textDraft,
                    textActionStatus = if (result.isSuccess) {
                        if (speak) "Speech request accepted" else "Instruction accepted"
                    } else null,
                    errorMessage = result.exceptionOrNull()?.message,
                )
            }
        }
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
}
