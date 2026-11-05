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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancelAndJoin

class ConversationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ConversationRepository()
    private val sessionManager = AgoraConversationSessionManager(application)
    private val _uiState = MutableStateFlow(ConversationUiStateMapper.freshUiState())

    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val modelFiles = com.androidengineers.agent_quickstart_android.rag.LocalModelFiles(application)
    private val localRag = com.androidengineers.agent_quickstart_android.rag.OnDeviceRag(application, modelFiles)
    private var documentJob: kotlinx.coroutines.Job? = null
    private var importJob: kotlinx.coroutines.Job? = null
    private val documentApi = com.androidengineers.agent_quickstart_android.data.ConversationAgoraApi()
    val documentState = MutableStateFlow(com.androidengineers.agent_quickstart_android.rag.DocumentDemoState(
        gemmaImported = modelFiles.gemmaReady, embeddingImported = modelFiles.embeddingReady,
    ))

    fun importModel(uri: android.net.Uri, generation: Boolean) {
        if (_uiState.value.inConversation || _uiState.value.isStarting || _uiState.value.isStopping || documentState.value.busy) return
        importJob = viewModelScope.launch {
            documentState.value = documentState.value.copy(busy = true, loaded = false, status = "Importing model…")
            try {
                localRag.release()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    modelFiles.import(uri, generation) { progress -> documentState.update { it.copy(status = progress) } }
                }
                documentState.update { it.copy(status = "Model imported. Import the other model, then open your PDF.") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) { documentState.update { it.copy(status = error.message ?: "Model import failed") }
            } finally {
                documentState.update { it.copy(busy = false, gemmaImported = modelFiles.gemmaReady, embeddingImported = modelFiles.embeddingReady) }
            }
        }
    }

    fun importDocument(uri: android.net.Uri) {
        if (_uiState.value.inConversation || _uiState.value.isStarting || _uiState.value.isStopping || documentState.value.busy) return
        if (!modelFiles.ready) {
            documentState.update { it.copy(status = "Import Gemma and EmbeddingGemma model files first.") }
            return
        }
        clearTransientMessages()
        importJob = viewModelScope.launch {
            documentState.value = documentState.value.copy(busy = true, loaded = false, status = "Opening document…", sources = emptyList(), answer = "", question = "")
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.androidengineers.agent_quickstart_android.rag.DocumentImporter.read(getApplication(), uri)
                }
                localRag.prepare(text) { progress -> documentState.update { it.copy(status = progress) } }
                documentState.update { it.copy(loaded = true, busy = false) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) {
                documentState.update { it.copy(loaded = false, busy = false, status = error.message ?: "Could not prepare document with on-device models") }
            }
        }
    }

    private fun serveDocument(channel: String, token: String) {
        documentJob?.cancel()
        documentJob = viewModelScope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                try {
                    val request = documentApi.pollDocument(channel, token)
                    val id = request.requestId ?: continue
                    val question = request.question ?: continue
                    documentState.update { it.copy(question = question, status = "Retrieving evidence and generating with Gemma on this phone…") }
                    val latestPartial = java.util.concurrent.atomic.AtomicReference("")
                    try {
                        kotlinx.coroutines.coroutineScope {
                            val partials = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.CONFLATED)
                            val sender = launch {
                                for (first in partials) {
                                    var snapshot = first
                                    kotlinx.coroutines.delay(150)
                                    while (true) snapshot = partials.tryReceive().getOrNull() ?: break
                                    documentApi.submitDocumentResult(channel, token, id, snapshot, final = false)
                                    documentState.update { it.copy(answer = snapshot, status = "Gemma is answering • streaming to Agora") }
                                }
                            }
                            val inference = async {
                                localRag.answer(question, request.history.orEmpty(), request.previousQuestion.orEmpty()) { snapshot ->
                                    latestPartial.set(snapshot)
                                    partials.trySend(snapshot)
                                }
                            }
                            val watcher = launch {
                                while (inference.isActive) {
                                    kotlinx.coroutines.delay(1000)
                                    if (!documentApi.documentRequestActive(channel, token, id)) {
                                        inference.cancel()
                                        break
                                    }
                                }
                            }
                            try {
                                val result = inference.await()
                                sender.cancelAndJoin()
                                documentState.update { it.copy(sources = result.evidence, answer = result.text,
                                    status = "Retrieval ${result.retrievalMs} ms • first text ${result.firstTokenMs} ms • generation ${result.generationMs} ms") }
                                documentApi.submitDocumentResult(channel, token, id, result.text)
                            } finally { watcher.cancel(); sender.cancel(); partials.close() }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        // The live session continues after an interrupted or superseded model turn.
                    } catch (error: Exception) {
                        documentState.update { it.copy(status = "On-device generation failed: ${error.message.orEmpty()}") }
                        runCatching { documentApi.submitDocumentResult(channel, token, id,
                            latestPartial.get().let { partial ->
                                if (partial.isBlank()) "I couldn't generate an answer on the phone. Please try again."
                                else (partial + " Sorry, I couldn't finish that answer.").take(2000)
                            }) }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                } catch (error: Exception) {
                    documentState.update { it.copy(status = "Phone connection interrupted. Retrying… ${error.message.orEmpty()}") }
                    kotlinx.coroutines.delay(1500)
                }
            }
        }
    }

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
        if (currentState.inConversation || currentState.isStarting || currentState.isStopping || documentState.value.busy) {
            return
        }
        if (!documentState.value.loaded) {
            _uiState.update { it.copy(errorMessage = if (modelFiles.ready) "Models are ready. Open a PDF or text document and wait for indexing before starting." else "Import the missing model files in Open document, then choose your PDF.") }
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
                repository.requireOnDeviceBackend()
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
                val documentToken = if (documentState.value.loaded) java.util.UUID.randomUUID().toString() else null
                val inviteAttempt = runCatching {
                    repository.inviteAgent(
                        channelName = bootstrap.channel,
                        requesterRtcUid = requesterRtcUid,
                        documentToken = documentToken,
                    )
                }
                val inviteResult = inviteAttempt.getOrNull()
                activeAgentId = inviteResult?.agentId
                sessionManager.setActiveAgentId(activeAgentId)
                if (activeAgentId != null && documentToken != null) serveDocument(bootstrap.channel, documentToken)

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
                documentJob?.cancel()
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
            documentJob?.cancel()

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
        documentJob?.cancel()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch { localRag.release() }
        sessionManager.release()
        super.onCleared()
    }
}
