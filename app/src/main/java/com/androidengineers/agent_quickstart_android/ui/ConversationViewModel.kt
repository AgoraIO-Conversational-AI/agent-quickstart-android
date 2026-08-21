package com.androidengineers.agent_quickstart_android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.data.ConversationRepository
import com.androidengineers.agent_quickstart_android.data.JournalRepository
import com.androidengineers.agent_quickstart_android.data.local.BetterSaidDatabase
import com.androidengineers.agent_quickstart_android.domain.conversation.CorrectionAnalysisUseCases
import com.androidengineers.agent_quickstart_android.domain.correction.ParsedCorrection
import com.androidengineers.agent_quickstart_android.domain.correction.parseCorrectionResponse
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.PracticeMode
import com.androidengineers.agent_quickstart_android.rtc.AgoraConversationSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ConversationRepository()
    private val journalRepository = JournalRepository(
        BetterSaidDatabase.getInstance(application).journalDao(),
    )
    private val sessionManager = AgoraConversationSessionManager(
        context = application,
        repository = repository,
    )
    private val correctionAnalysis = CorrectionAnalysisUseCases()
    private val _uiState = MutableStateFlow(ConversationUiStateMapper.freshUiState())

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeAgentId: String? = null
    private var themeInitialized: Boolean = false
    private val savedJournalCorrectionIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            journalRepository.entries.collectLatest { entries ->
                _uiState.update { it.copy(journalEntries = entries) }
            }
        }

        viewModelScope.launch {
            sessionManager.snapshot.collectLatest { snapshot ->
                val merged = withContext(Dispatchers.Default) {
                    ConversationUiStateMapper.mergeSession(_uiState.value, snapshot)
                }
                val correctedState = correctionAnalysis.captureCorrectionResponseIfReady(merged)
                _uiState.value = correctedState
                saveJournalEntryIfCorrectionReady(correctedState)
            }
        }
    }

    fun updateMicrophonePermission(granted: Boolean) {
        _uiState.update { it.copy(microphonePermissionGranted = granted) }
    }

    fun showMicrophonePermissionDenied() {
        _uiState.update {
            it.copy(
                errorMessage = "Microphone permission is blocked. Open Android app settings, allow microphone access, then restart the practice session.",
                warningMessage = null,
            )
        }
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

    fun selectPracticeMode(mode: PracticeMode) {
        _uiState.update { it.copy(practiceMode = mode) }
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
                        practiceMode = _uiState.value.practiceMode,
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
                    journalEntries = _uiState.value.journalEntries,
                )
            }
        }
    }

    fun endConversation() {
        val currentState = _uiState.value
        if (currentState.isStopping || currentState.isStarting) {
            return
        }

        val agentIdToStop = activeAgentId
        val channelNameToStop = sessionManager.snapshot.value.channelName

        activeAgentId = null
        sessionManager.setActiveAgentId(null)

        // freshUiState() must be written BEFORE disconnect() so that any collectLatest
        // emission triggered by the snapshot reset already reads inConversation=false.
        _uiState.value = ConversationUiStateMapper.freshUiState(
            permissionGranted = currentState.microphonePermissionGranted,
            isDarkTheme = currentState.isDarkTheme,
            journalEntries = currentState.journalEntries,
        )
        sessionManager.disconnect(resetSnapshot = true)

        // Fire-and-forget: tell the agent to leave without blocking the UI transition.
        if (agentIdToStop != null && channelNameToStop != null) {
            viewModelScope.launch {
                runCatching { repository.stopConversation(agentIdToStop, channelNameToStop) }
            }
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

        val userTranscript = correctionAnalysis.userTranscriptForCorrection(currentState)
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
                    correctionRequestedAgentTurnCount = correctionAnalysis.completedAgentTurnCount(it),
                    correctionOriginalText = userTranscript,
                    correctionResponseText = null,
                    correctionAgentTurnKey = null,
                    errorMessage = null,
                    warningMessage = null,
                )
            }

            val sendFailed = runCatching {
                sessionManager.sendTextToAgent(
                    text = correctionAnalysis.buildAnalysisPrompt(userTranscript),
                    priority = "INTERRUPT",
                    responseInterruptable = false,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAnalyzingCorrection = false,
                        correctionRequestedAtMillis = null,
                        correctionRequestedAgentTurnCount = 0,
                        correctionOriginalText = null,
                        correctionResponseText = null,
                        correctionAgentTurnKey = null,
                        errorMessage = error.message ?: "Unable to ask BetterSaid to analyze your English.",
                    )
                }
            }.isFailure

            if (sendFailed) return@launch

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

    private suspend fun saveJournalEntryIfCorrectionReady(state: ConversationUiState) {
        val responseText = state.correctionResponseText ?: return
        val originalText = state.correctionOriginalText?.takeIf { it.isNotBlank() } ?: return
        val correctionId = state.correctionAgentTurnKey
            ?.let { "correction-$it" }
            ?: "correction-${state.correctionRequestedAtMillis ?: return}"
        if (!savedJournalCorrectionIds.add(correctionId)) {
            return
        }

        val parsedCorrection = parseCorrectionResponse(
            originalFallback = originalText,
            response = responseText,
        )
        if (!parsedCorrection.hasCorrectedSentence ||
            parsedCorrection.corrected.normalizeForJournal() == originalText.normalizeForJournal()
        ) {
            return
        }

        journalRepository.saveCorrection(
            id = correctionId,
            originalText = parsedCorrection.original.ifBlank { originalText },
            correctedText = parsedCorrection.corrected,
            tipText = parsedCorrection.tip,
            practiceMode = state.practiceMode,
            tags = parsedCorrection.journalTags(),
            spokenAtMillis = state.correctionRequestedAtMillis ?: System.currentTimeMillis(),
        )
    }

    override fun onCleared() {
        sessionManager.release()
        super.onCleared()
    }

    private companion object {
        const val CORRECTION_ANALYSIS_TIMEOUT_MS = 20_000L
    }
}

private fun ParsedCorrection.journalTags(): List<String> {
    val tip = tip.lowercase()
    return buildList {
        if ("tense" in tip || "verb" in tip || "past" in tip || "present" in tip) {
            add("Tense")
        }
        if ("preposition" in tip || " in " in tip || " on " in tip || " at " in tip || " to " in tip) {
            add("Preposition")
        }
        if ("article" in tip || " a " in tip || " an " in tip || " the " in tip) {
            add("Article")
        }
        if ("plural" in tip || "singular" in tip || "uncountable" in tip) {
            add("Nouns")
        }
        if ("gerund" in tip || "ing" in tip) {
            add("Gerund")
        }
        if (isEmpty() && changePairs.isNotEmpty()) {
            add("Correction")
        }
    }
}

private fun String.normalizeForJournal(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim('"', '\'', '“', '”', '.', '!', '?')
}
