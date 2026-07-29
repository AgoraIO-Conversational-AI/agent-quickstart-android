package com.androidengineers.agent_quickstart_android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.data.ConversationRepository
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.rtc.AgoraConversationSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
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
                    val merged = ConversationUiStateMapper.mergeSession(current, snapshot)
                    merged.captureCorrectionResponseIfReady()
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
                val inviteResult = runCatching {
                    repository.inviteAgent(
                        channelName = bootstrap.channel,
                        requesterRtcUid = requesterRtcUid,
                    )
                }.getOrNull()
                activeAgentId = inviteResult?.agentId
                sessionManager.setActiveAgentId(activeAgentId)

                val warning = if (inviteResult?.agentId == null) {
                    "The Android client joined the channel, but the direct Agora REST agent start did not succeed. Verify AGORA_APP_CERTIFICATE and your Agora project settings."
                } else {
                    null
                }

                _uiState.update { current ->
                    ConversationUiStateMapper.mergeSession(
                        current.copy(
                            isStarting = false,
                            inConversation = true,
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
                    errorMessage = error.message ?: "Unable to start the Agora conversation.",
                    isDarkTheme = _uiState.value.isDarkTheme,
                )
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
                    "The local session ended, but the direct Agora REST leave request failed: $message"
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

    fun endConversationForLifecycle() {
        val currentState = _uiState.value
        if (currentState.inConversation || currentState.isAnalyzingCorrection) {
            endConversation()
        }
    }

    fun doneSpeakingForCorrection() {
        val currentState = _uiState.value
        if (!currentState.inConversation || currentState.isAnalyzingCorrection) {
            return
        }

        val userTranscript = currentState.userTranscriptForCorrection()
        if (userTranscript.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Speak at least one sentence before asking BetterSaid to correct it.",
                    warningMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            val requestedAt = System.currentTimeMillis()
            sessionManager.setMicrophoneEnabled(false)
            _uiState.update {
                it.copy(
                    isAnalyzingCorrection = true,
                    correctionRequestedAtMillis = requestedAt,
                    correctionRequestedAgentTurnCount = it.completedAgentTurnCount(),
                    correctionOriginalText = userTranscript,
                    correctionResponseText = null,
                    correctionAgentTurnKey = null,
                    errorMessage = null,
                    warningMessage = null,
                )
            }

            runCatching {
                sessionManager.sendTextToAgent(
                    text = buildCorrectionAnalysisPrompt(userTranscript),
                    priority = "INTERRUPT",
                    responseInterruptable = false,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAnalyzingCorrection = false,
                        errorMessage = error.message ?: "Unable to ask BetterSaid to analyze your English.",
                    )
                }
            }

            delay(CORRECTION_ANALYSIS_TIMEOUT_MS)
            _uiState.update {
                if (it.isAnalyzingCorrection && it.correctionRequestedAtMillis == requestedAt) {
                    it.copy(
                        isAnalyzingCorrection = false,
                        warningMessage = "BetterSaid did not return a correction yet. You can tap Done Speaking again or keep talking to the coach.",
                    )
                } else {
                    it
                }
            }
        }
    }

    fun askCoachAboutMistakes() {
        if (!_uiState.value.inConversation) {
            return
        }
        sessionManager.setMicrophoneEnabled(true)
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

    private fun ConversationUiState.userTranscriptForCorrection(): String {
        val completedTurns = transcriptHistory
            .filter { it.speaker == TranscriptSpeaker.USER && it.text.isNotBlank() }
            .joinToString(" ") { it.text.trim() }
        val liveTurn = liveTranscript
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

    private fun ConversationUiState.captureCorrectionResponseIfReady(): ConversationUiState {
        correctionRequestedAtMillis ?: return this
        if (correctionResponseText != null) {
            return this
        }
        val correctionTurn = transcriptHistory
            .filter { it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank() }
            .drop(correctionRequestedAgentTurnCount)
            .firstOrNull()

        return if (correctionTurn == null) {
            this
        } else {
            copy(
                isAnalyzingCorrection = false,
                correctionResponseText = correctionTurn.text,
                correctionAgentTurnKey = correctionTurn.key,
            )
        }
    }

    private fun buildCorrectionAnalysisPrompt(transcript: String): String {
        return """
BETTERSAID_ANALYZE_TRANSCRIPT

Learner transcript:
$transcript
        """.trimIndent()
    }

    private fun ConversationUiState.completedAgentTurnCount(): Int {
        return transcriptHistory.count {
            it.speaker == TranscriptSpeaker.AGENT && it.text.isNotBlank()
        }
    }

    private companion object {
        const val CORRECTION_ANALYSIS_TIMEOUT_MS = 20_000L
    }
}
