package com.androidengineers.agent_quickstart_android.rtc

import android.content.Context
import android.util.Log
import com.androidengineers.agent_quickstart_android.audio.AudioSessionManager
import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.RenewalTokens
import com.androidengineers.agent_quickstart_android.model.SessionIssue
import com.androidengineers.agent_quickstart_android.model.SessionMetric
import com.androidengineers.agent_quickstart_android.model.SessionSnapshot
import io.agora.conversational.api.AgentManualEosEvent
import io.agora.conversational.api.AgentState
import io.agora.conversational.api.ConversationalAIAPIConfig
import io.agora.conversational.api.ConversationalAIAPIError
import io.agora.conversational.api.ConversationalAIAPIImpl
import io.agora.conversational.api.IConversationalAIAPI
import io.agora.conversational.api.IConversationalAIAPIEventHandler
import io.agora.conversational.api.InterruptEvent
import io.agora.conversational.api.MessageError
import io.agora.conversational.api.MessageReceipt
import io.agora.conversational.api.Metric
import io.agora.conversational.api.ModuleError
import io.agora.conversational.api.Priority
import io.agora.conversational.api.SpeakMessage
import io.agora.conversational.api.StateChangeEvent
import io.agora.conversational.api.ThinkListeningAction
import io.agora.conversational.api.ThinkMessage
import io.agora.conversational.api.ThinkSpeakingAction
import io.agora.conversational.api.ThinkThinkingAction
import io.agora.conversational.api.Transcript
import io.agora.conversational.api.TranscriptRenderMode
import io.agora.conversational.api.TranscriptStatus
import io.agora.conversational.api.TranscriptType
import io.agora.conversational.api.Turn
import io.agora.conversational.api.UserManualEosEvent
import io.agora.conversational.api.UserManualSosEvent
import io.agora.conversational.api.VoiceprintStateChangeEvent
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtm.ErrorInfo
import io.agora.rtm.LinkStateEvent
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmConfig
import io.agora.rtm.RtmConstants
import io.agora.rtm.RtmEventListener
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AgoraConversationSessionManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
    private val renewMutex = Mutex()
    private val transcriptAssembler = TranscriptAssembler()
    private val audioSessionManager = AudioSessionManager(
        context = appContext,
    ) { source, code, message ->
        addIssue(source = source, code = code, message = message)
    }
    private val _snapshot = MutableStateFlow(SessionSnapshot())

    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    private var rtcEngine: RtcEngine? = null
    private var rtmClient: RtmClient? = null
    private var conversationalApi: IConversationalAIAPI? = null
    private var currentChannel: String? = null
    private var localRtcUid: Int = 0
    private var currentAgentRtcUid: Int = 0
    private var currentAgentUserId: String? = null
    private var micRequestedEnabled: Boolean = true
    private var renewTokensProvider: (suspend (String, Int, String) -> RenewalTokens)? = null
    private var joinDeferred: CompletableDeferred<Int>? = null
    private var currentRtmUserId: String? = null
    private var currentAgentTurnId: Long? = null
    private var interruptRequestedTurnId: Long? = null
    private var lastInterruptRequestAtMs: Long = 0L
    private var agentListening: Boolean = false
    private var agentThinking: Boolean = false
    private var agentSpeaking: Boolean = false

    init {
        scope.launch {
            audioSessionManager.snapshot.collectLatest { audioSnapshot ->
                updateSnapshot { current ->
                    current.copy(
                        turnState = audioSnapshot.turnState,
                        audioSourceLabel = audioSnapshot.audioSourceLabel,
                        aecAvailable = audioSnapshot.aecAvailable,
                        aecEnabled = audioSnapshot.aecEnabled,
                        noiseSuppressorEnabled = audioSnapshot.noiseSuppressorEnabled,
                        ttsQueueSize = audioSnapshot.ttsQueueSize,
                        lastVadResult = audioSnapshot.lastVadResult,
                        lastBargeInEvent = audioSnapshot.lastBargeInEvent,
                    )
                }
            }
        }
    }

    suspend fun connect(
        bootstrap: AgoraTokenBundle,
        onRenewTokens: suspend (String, Int, String) -> RenewalTokens,
    ) {
        disconnect(resetSnapshot = true)
        currentChannel = bootstrap.channel
        currentAgentRtcUid = bootstrap.agentRtcUid
        currentAgentUserId = bootstrap.agentRtcUid.takeIf { it > 0 }?.toString()
        renewTokensProvider = onRenewTokens
        transcriptAssembler.reset()
        micRequestedEnabled = true
        currentRtmUserId = bootstrap.rtmUserId
        currentAgentTurnId = null
        interruptRequestedTurnId = null
        lastInterruptRequestAtMs = 0L
        resetAgentActivity()
        _snapshot.value = SessionSnapshot(
            channelName = bootstrap.channel,
            micEnabled = currentMicEnabled(),
            micRequestedEnabled = micRequestedEnabled,
            micAutoMuted = false,
        )

        try {
            ensureRtcEngine(bootstrap.appId)
            ensureRtmClient(
                appId = bootstrap.appId,
                token = bootstrap.rtmToken,
                channel = bootstrap.channel,
                userId = bootstrap.rtmUserId,
            )
            ensureConversationalApi()
            subscribeToolkitMessages(bootstrap.channel)
            joinRtcChannel(bootstrap)
            audioSessionManager.start()
            audioSessionManager.setMicrophoneEnabled(micRequestedEnabled)
        } catch (error: Throwable) {
            disconnect(resetSnapshot = true)
            throw error
        }
    }

    fun disconnect(resetSnapshot: Boolean = true) {
        joinDeferred?.cancel()
        joinDeferred = null
        transcriptAssembler.reset()
        renewTokensProvider = null
        localRtcUid = 0
        currentAgentRtcUid = 0
        currentAgentUserId = null
        micRequestedEnabled = true
        currentRtmUserId = null
        currentAgentTurnId = null
        interruptRequestedTurnId = null
        lastInterruptRequestAtMs = 0L
        resetAgentActivity()
        audioSessionManager.stop()

        val channel = currentChannel
        currentChannel = null

        conversationalApi?.let { api ->
            if (!channel.isNullOrBlank()) {
                runCatching { api.unsubscribeMessage(channel, noopConversationalCallback()) }
            }
            runCatching { api.removeHandler(conversationalEventHandler) }
            runCatching { api.destroy() }
        }
        conversationalApi = null

        rtmClient?.let { client ->
            runCatching { client.removeEventListener(rtmEventListener) }
            runCatching { client.logout(noopRtmCallback()) }
        }
        rtmClient = null

        rtcEngine?.let { engine ->
            runCatching { engine.leaveChannel() }
        }
        rtcEngine = null
        runCatching { RtcEngine.destroy() }

        if (resetSnapshot) {
            _snapshot.value = SessionSnapshot()
        }
    }

    fun release() {
        disconnect(resetSnapshot = true)
        sessionJob.cancel()
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        micRequestedEnabled = enabled
        audioSessionManager.setMicrophoneEnabled(enabled)
        syncMicState()
    }

    suspend fun sendText(
        text: String,
        speak: Boolean,
        append: Boolean,
    ) {
        val api = conversationalApi ?: throw IOException("Conversational AI toolkit is not initialized.")
        val agentUserId = currentAgentUserId ?: throw IOException("Agent RTM user ID is not available yet.")
        if (speak) {
            awaitConversationalAction { completion ->
                api.speak(
                    agentUserId,
                    SpeakMessage(
                        text = text,
                        priority = if (append) Priority.APPEND else Priority.INTERRUPT,
                        interruptable = true,
                    ),
                    completion,
                )
            }
        } else {
            val action = if (append) {
                ToolkitThinkAction.APPEND
            } else {
                ToolkitThinkAction.INTERRUPT
            }
            awaitConversationalAction { completion ->
                api.think(
                    agentUserId,
                    ThinkMessage(
                        text = text,
                        onListeningAction = action.listening,
                        onThinkingAction = action.thinking,
                        onSpeakingAction = action.speaking,
                        interruptable = true,
                    ),
                    completion,
                )
            }
        }
    }

    private suspend fun ensureRtcEngine(appId: String) = withContext(Dispatchers.Main.immediate) {
        if (rtcEngine != null) {
            return@withContext
        }

        val config = RtcEngineConfig().apply {
            mContext = appContext
            mAppId = appId
            mEventHandler = rtcEventHandler
            mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            mAudioScenario = Constants.AUDIO_SCENARIO_AI_CLIENT
        }

        val engine = RtcEngine.create(config)
            ?: throw IllegalStateException(
                "Agora RTC engine failed to initialize. Verify AGORA_APP_ID and required Android network/audio permissions."
            )

        checkRtcResult(
            operation = "enableAudio",
            result = engine.enableAudio(),
        )
        runRtcBestEffort(
            operation = "setDefaultAudioRoutetoSpeakerphone",
            result = engine.setDefaultAudioRoutetoSpeakerphone(true),
        )
        runRtcBestEffort(
            operation = "setAudioProfile",
            result = engine.setAudioProfile(
                Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                Constants.AUDIO_SCENARIO_AI_CLIENT,
            ),
        )
        audioSessionManager.configureRtcEngine(engine)
        rtcEngine = engine
    }

    private suspend fun ensureRtmClient(
        appId: String,
        token: String,
        channel: String,
        userId: String,
    ) {
        val client = RtmClient.create(
            RtmConfig.Builder(appId, userId)
                .useStringUserId(true)
                .build()
        )
        client.addEventListener(rtmEventListener)

        try {
            awaitRtmVoid { callback -> client.login(token, callback) }
            rtmClient = client
        } catch (error: Throwable) {
            runCatching { client.removeEventListener(rtmEventListener) }
            runCatching { client.logout(noopRtmCallback()) }
            throw error
        }
    }

    private fun ensureConversationalApi() {
        if (conversationalApi != null) {
            return
        }
        val engine = rtcEngine ?: throw IllegalStateException("RTC engine is not initialized.")
        val client = rtmClient ?: throw IllegalStateException("RTM client is not initialized.")
        conversationalApi = ConversationalAIAPIImpl(
            ConversationalAIAPIConfig(
                rtcEngine = engine,
                rtmClient = client,
                renderMode = TranscriptRenderMode.Word,
                enableLog = true,
                enableRenderModeFallback = true,
            )
        ).also { api ->
            api.addHandler(conversationalEventHandler)
        }
    }

    private suspend fun subscribeToolkitMessages(channel: String) {
        val api = conversationalApi ?: throw IllegalStateException("Conversational AI toolkit is not initialized.")
        awaitConversationalAction { completion ->
            api.subscribeMessage(channel, completion)
        }
    }

    private suspend fun joinRtcChannel(bootstrap: AgoraTokenBundle) {
        val engine = rtcEngine ?: throw IllegalStateException("RTC engine is not initialized.")
        val requestedUid = bootstrap.uid.toIntOrNull() ?: 0
        val deferred = CompletableDeferred<Int>()
        joinDeferred = deferred

        val result = withContext(Dispatchers.Main.immediate) {
            conversationalApi?.loadAudioSettings(
                Constants.AUDIO_SCENARIO_AI_CLIENT,
                enableAins = false,
            )
            engine.joinChannel(
                bootstrap.rtcToken,
                bootstrap.channel,
                requestedUid,
                ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    publishMicrophoneTrack = true
                    publishCustomAudioTrack = false
                    autoSubscribeAudio = true
                    autoSubscribeVideo = false
                    enableAudioRecordingOrPlayout = true
                }
            )
        }

        Log.i(
            TAG,
            "rtc_publish_config native_mic_track_active=true custom_audio_track_active=false"
        )

        if (result != Constants.ERR_OK) {
            joinDeferred = null
            throw IOException(
                "RTC join failed (${RtcEngine.getErrorDescription(result)})."
            )
        }

        try {
            withTimeout(RTC_JOIN_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (error: TimeoutCancellationException) {
            if (joinDeferred == deferred) {
                joinDeferred = null
            }
            throw IOException(
                "RTC join timed out after ${RTC_JOIN_TIMEOUT_MS / 1000} seconds.",
                error,
            )
        } catch (error: CancellationException) {
            if (joinDeferred == deferred) {
                joinDeferred = null
            }
            throw error
        }
    }

    private fun updateSnapshot(transform: (SessionSnapshot) -> SessionSnapshot) {
        _snapshot.update(transform)
    }

    private fun addIssue(
        source: String,
        code: String,
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        updateSnapshot { current ->
            val duplicate = current.issues.any { issue ->
                issue.source == source &&
                    issue.code == code &&
                    issue.message == message &&
                    abs(issue.timestampMillis - timestampMillis) < 1_500L
            }
            if (duplicate) {
                current
            } else {
                current.copy(
                    issues = buildList {
                        add(
                            SessionIssue(
                                id = "$timestampMillis-$source-$code",
                                source = source,
                                code = code,
                                message = message,
                                timestampMillis = timestampMillis,
                            )
                        )
                        addAll(current.issues)
                    }.take(6)
                )
            }
        }
    }

    private fun updateTranscript(transcript: Transcript, agentUserId: String) {
        updateSnapshot { current ->
            current.copy(
                transcriptTurns = transcriptAssembler.handleTranscript(
                    transcript = transcript,
                    agentUserId = agentUserId,
                    localRtcUid = localRtcUid,
                )
            )
        }
    }

    private fun markAgentInterrupted(turnId: Long?) {
        updateSnapshot { current ->
            current.copy(
                transcriptTurns = transcriptAssembler.handlePayload(
                    payload = TranscriptPayload(
                        objectType = "message.interrupt",
                        turnId = turnId,
                    ),
                    localRtcUid = localRtcUid,
                )
            )
        }
    }

    private fun updateAgentState(
        rawState: String,
        turnId: Long?,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val mappedState = rawState.toAgentConversationState()
        audioSessionManager.onAgentStateChanged(mappedState)

        when (mappedState) {
            AgentConversationState.SPEAKING -> {
                currentAgentTurnId = turnId
                audioSessionManager.setMicrophoneEnabled(micRequestedEnabled)
                Log.i(TAG, "Agent speaking with microphone kept ${if (micRequestedEnabled) "enabled" else "muted"} for barge-in")
            }

            AgentConversationState.LISTENING,
            AgentConversationState.IDLE,
            AgentConversationState.SILENT -> {
                currentAgentTurnId = null
                interruptRequestedTurnId = null
                audioSessionManager.setMicrophoneEnabled(micRequestedEnabled)
                Log.i(TAG, "Agent ready; microphone restored to requested state")
            }

            else -> Unit
        }

        updateSnapshot { current ->
            current.copy(agentState = mappedState)
        }
        if (mappedState == AgentConversationState.UNKNOWN) {
            addIssue(
                source = "rtm-presence",
                code = turnId?.toString() ?: "unknown-turn",
                message = "Received an unknown agent state: $rawState",
                timestampMillis = timestampMillis,
            )
        }
    }

    private fun renewTokens() {
        scope.launch {
            renewMutex.withLock {
                val channel = currentChannel ?: return@withLock
                val provider = renewTokensProvider ?: return@withLock
                val rtcUid = localRtcUid
                val rtmUserId = currentRtmUserId ?: return@withLock

                try {
                    val tokens = provider(channel, rtcUid, rtmUserId)
                    rtcEngine?.renewToken(tokens.rtcToken)
                    rtmClient?.let { client ->
                        try {
                            awaitRtmVoid { callback -> client.renewToken(tokens.rtmToken, callback) }
                        } catch (error: Throwable) {
                            addIssue(
                                source = "rtm",
                                code = "renew-token",
                                message = error.message ?: "Failed to renew the RTM token.",
                            )
                        }
                    }
                } catch (error: Throwable) {
                    addIssue(
                        source = "backend",
                        code = "renew-token",
                        message = error.message ?: "Failed to renew tokens through the quickstart server.",
                    )
                }
            }
        }
    }

    private fun currentMicEnabled(): Boolean {
        return micRequestedEnabled
    }

    private fun syncMicState() {
        updateSnapshot {
            it.copy(
                micEnabled = currentMicEnabled(),
                micRequestedEnabled = micRequestedEnabled,
                micAutoMuted = false,
            )
        }
    }

    private fun String.toAgentConversationState(): AgentConversationState {
        return when (lowercase(Locale.ROOT)) {
            "idle" -> AgentConversationState.IDLE
            "listening" -> AgentConversationState.LISTENING
            "thinking" -> AgentConversationState.THINKING
            "speaking" -> AgentConversationState.SPEAKING
            "silent" -> AgentConversationState.SILENT
            else -> AgentConversationState.UNKNOWN
        }
    }

    private fun requestAgentInterruptFromUserSpeech(text: String) {
        if (text.isBlank()) {
            return
        }
        val agentUserId = currentAgentUserId ?: return
        val api = conversationalApi ?: return
        if (_snapshot.value.agentState != AgentConversationState.SPEAKING) {
            return
        }

        val turnId = currentAgentTurnId
        if (turnId != null && interruptRequestedTurnId == turnId) {
            return
        }

        val now = System.currentTimeMillis()
        if (turnId == null && now - lastInterruptRequestAtMs < 1_500L) {
            return
        }

        interruptRequestedTurnId = turnId
        lastInterruptRequestAtMs = now
        scope.launch {
            runCatching {
                awaitConversationalAction { completion ->
                    api.interrupt(agentUserId, completion)
                }
            }.onSuccess {
                Log.i(
                    TAG,
                    "interrupt_event_sent agentUserId=$agentUserId reason=user-transcription turnId=${turnId ?: "none"}"
                )
            }.onFailure { error ->
                addIssue(
                    source = "interrupt",
                    code = turnId?.toString() ?: "user-transcription",
                    message = error.message ?: "Failed to interrupt the cloud agent.",
                )
            }
        }
    }

    private fun handleToolkitTranscript(agentUserId: String, transcript: Transcript) {
        when (transcript.type) {
            TranscriptType.USER -> {
                val agentSpeaking = _snapshot.value.agentState == AgentConversationState.SPEAKING
                if (audioSessionManager.shouldAcceptUserTranscript(transcript.text)) {
                    audioSessionManager.onUserTranscriptAccepted(
                        interruptingAgent = agentSpeaking,
                        isFinal = transcript.status != TranscriptStatus.IN_PROGRESS,
                    )
                    if (agentSpeaking) {
                        requestAgentInterruptFromUserSpeech(transcript.text)
                    }
                    updateTranscript(transcript, agentUserId)
                } else {
                    Log.i(TAG, "Discarded self-speech transcript.")
                }
            }

            TranscriptType.AGENT -> {
                audioSessionManager.onAssistantTranscript(transcript.text)
                updateTranscript(transcript, agentUserId)
            }
        }
    }

    private fun updateIndependentAgentState(
        listening: Boolean? = null,
        thinking: Boolean? = null,
        speaking: Boolean? = null,
    ) {
        listening?.let { agentListening = it }
        thinking?.let { agentThinking = it }
        speaking?.let { agentSpeaking = it }

        val state = when {
            agentSpeaking -> AgentState.SPEAKING
            agentThinking -> AgentState.THINKING
            agentListening -> AgentState.LISTENING
            else -> AgentState.IDLE
        }
        updateAgentState(
            rawState = state.value,
            turnId = currentAgentTurnId,
        )
    }

    private fun resetAgentActivity() {
        agentListening = false
        agentThinking = false
        agentSpeaking = false
    }

    private fun addMetric(metric: SessionMetric) {
        updateSnapshot { current ->
            current.copy(
                metrics = buildList {
                    add(metric)
                    addAll(current.metrics)
                }.take(4)
            )
        }
    }

    private fun formatMetricValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            "${value.toLong()} ms"
        } else {
            String.format(Locale.US, "%.1f ms", value)
        }
    }

    private suspend fun awaitRtmVoid(
        block: (ResultCallback<Void>) -> Unit,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        block(
            object : ResultCallback<Void> {
                override fun onSuccess(result: Void?) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(errorInfo: ErrorInfo) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IOException(
                                "${errorInfo.getErrorCode()}: ${errorInfo.getErrorReason()}"
                            )
                        )
                    }
                }
            }
        )
    }

    private fun noopRtmCallback(): ResultCallback<Void> {
        return object : ResultCallback<Void> {
            override fun onSuccess(result: Void?) = Unit
            override fun onFailure(errorInfo: ErrorInfo) = Unit
        }
    }

    private fun noopConversationalCallback(): (ConversationalAIAPIError?) -> Unit = {}

    private suspend fun awaitConversationalAction(
        block: ((ConversationalAIAPIError?) -> Unit) -> Unit,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        block { error ->
            if (!continuation.isActive) {
                return@block
            }
            if (error == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    IOException("${error.errorCode}: ${error.errorMessage}")
                )
            }
        }
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            localRtcUid = uid
            joinDeferred?.complete(uid)
            joinDeferred = null
            rtcEngine?.let { engine ->
                runRtcBestEffort(
                    operation = "setEnableSpeakerphone",
                    result = engine.setEnableSpeakerphone(true),
                )
            }
            updateSnapshot {
                it.copy(
                    channelName = channel,
                    localRtcUid = uid,
                    rtcConnectionState = Constants.CONNECTION_STATE_CONNECTED,
                    rtcConnectionReason = Constants.CONNECTION_CHANGED_JOIN_SUCCESS,
                )
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            if (uid == currentAgentRtcUid) {
                updateSnapshot { it.copy(isAgentRtcConnected = true) }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            if (uid == currentAgentRtcUid) {
                updateSnapshot { it.copy(isAgentRtcConnected = false) }
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            updateSnapshot {
                it.copy(
                    rtcConnectionState = state,
                    rtcConnectionReason = reason,
                )
            }
            if (state == Constants.CONNECTION_STATE_FAILED) {
                joinDeferred?.completeExceptionally(
                    IOException("RTC connection failed (${RtcEngine.getErrorDescription(reason)}).")
                )
                joinDeferred = null
                addIssue(
                    source = "rtc",
                    code = reason.toString(),
                    message = "RTC connection failed (${RtcEngine.getErrorDescription(reason)}).",
                )
            }
        }

        override fun onError(errorCode: Int) {
            addIssue(
                source = "rtc",
                code = errorCode.toString(),
                message = "Agora RTC error: ${RtcEngine.getErrorDescription(errorCode)}",
            )
        }

        override fun onTokenPrivilegeWillExpire(token: String) {
            renewTokens()
        }
    }

    private val rtmEventListener = object : RtmEventListener {
        override fun onLinkStateEvent(event: LinkStateEvent) {
            updateSnapshot {
                it.copy(
                    rtmConnectionState = event.getCurrentState().name,
                    rtmLinkState = event.getOperation().name,
                )
            }
            when (event.getCurrentState()) {
                RtmConstants.RtmLinkState.FAILED,
                RtmConstants.RtmLinkState.DISCONNECTED,
                -> addIssue(
                    source = "rtm",
                    code = event.getReasonCode().name,
                    message = "RTM link ${event.getCurrentState().name.lowercase(Locale.ROOT)} (${event.getReasonCode().name}).",
                )

                else -> Unit
            }
        }
    }

    private val conversationalEventHandler = object : IConversationalAIAPIEventHandler {
        override fun onAgentStateChanged(agentUserId: String, event: StateChangeEvent) {
            currentAgentUserId = agentUserId
            updateAgentState(
                rawState = event.state.value,
                turnId = event.turnId,
                timestampMillis = event.timestamp,
            )
        }

        override fun onAgentListeningChanged(agentUserId: String, isListening: Boolean) {
            currentAgentUserId = agentUserId
            updateIndependentAgentState(listening = isListening)
        }

        override fun onAgentThinkingChanged(agentUserId: String, isThinking: Boolean) {
            currentAgentUserId = agentUserId
            updateIndependentAgentState(thinking = isThinking)
        }

        override fun onAgentSpeakingChanged(agentUserId: String, isSpeaking: Boolean) {
            currentAgentUserId = agentUserId
            updateIndependentAgentState(speaking = isSpeaking)
        }

        override fun onAgentInterrupted(agentUserId: String, event: InterruptEvent) {
            currentAgentUserId = agentUserId
            markAgentInterrupted(event.turnId)
        }

        override fun onAgentMetrics(agentUserId: String, metric: Metric) {
            currentAgentUserId = agentUserId
            addMetric(
                SessionMetric(
                    label = "${metric.type.value}.${metric.name}",
                    value = formatMetricValue(metric.value),
                    timestampMillis = metric.timestamp,
                )
            )
        }

        override fun onTurnFinished(agentUserId: String, turn: Turn) {
            currentAgentUserId = agentUserId
            addMetric(
                SessionMetric(
                    label = "turn ${turn.turnId} e2e",
                    value = formatMetricValue(turn.e2eLatency),
                    timestampMillis = turn.timestamp,
                )
            )
        }

        override fun onAgentError(agentUserId: String, error: ModuleError) {
            currentAgentUserId = agentUserId
            addIssue(
                source = "toolkit-${error.type.value}",
                code = error.code.toString(),
                message = error.message.ifBlank { "Agent module error." },
                timestampMillis = error.timestamp,
            )
        }

        override fun onMessageError(agentUserId: String, error: MessageError) {
            currentAgentUserId = agentUserId
            addIssue(
                source = "toolkit-message",
                code = error.code.toString(),
                message = "${error.chatMessageType.value}: ${error.message}",
                timestampMillis = error.timestamp,
            )
        }

        override fun onMessageReceiptUpdated(agentUserId: String, receipt: MessageReceipt) {
            currentAgentUserId = agentUserId
            Log.d(TAG, "toolkit_message_receipt agentUserId=$agentUserId type=${receipt.type.value} turnId=${receipt.turnId}")
        }

        override fun onAgentVoiceprintStateChanged(agentUserId: String, event: VoiceprintStateChangeEvent) {
            currentAgentUserId = agentUserId
            addIssue(
                source = "toolkit-voiceprint",
                code = event.status.value,
                message = "Voiceprint status: ${event.status.value}",
                timestampMillis = event.timestamp,
            )
        }

        override fun onUserManualSosEvent(agentUserId: String, event: UserManualSosEvent) {
            currentAgentUserId = agentUserId
            Log.d(TAG, "toolkit_manual_sos agentUserId=$agentUserId success=${event.payload.success}")
        }

        override fun onUserManualEosEvent(agentUserId: String, event: UserManualEosEvent) {
            currentAgentUserId = agentUserId
            Log.d(TAG, "toolkit_manual_eos agentUserId=$agentUserId success=${event.payload.success}")
        }

        override fun onAgentManualEosEvent(agentUserId: String, event: AgentManualEosEvent) {
            currentAgentUserId = agentUserId
            Log.d(TAG, "toolkit_agent_manual_eos agentUserId=$agentUserId reason=${event.payload.reason}")
        }

        override fun onTranscriptUpdated(agentUserId: String, transcript: Transcript) {
            currentAgentUserId = agentUserId
            handleToolkitTranscript(agentUserId, transcript)
        }

        override fun onDebugLog(log: String) {
            Log.v(TAG, log)
        }
    }

    companion object {
        private const val TAG = "AgoraConversationSession"
        private const val RTC_JOIN_TIMEOUT_MS = 20_000L
    }

    private enum class ToolkitThinkAction(
        val listening: ThinkListeningAction,
        val thinking: ThinkThinkingAction,
        val speaking: ThinkSpeakingAction,
    ) {
        INTERRUPT(
            listening = ThinkListeningAction.INTERRUPT,
            thinking = ThinkThinkingAction.INTERRUPT,
            speaking = ThinkSpeakingAction.INTERRUPT,
        ),
        APPEND(
            listening = ThinkListeningAction.APPEND,
            thinking = ThinkThinkingAction.APPEND,
            speaking = ThinkSpeakingAction.APPEND,
        ),
    }

    private fun checkRtcResult(
        operation: String,
        result: Int,
    ) {
        if (result != Constants.ERR_OK) {
            throw IllegalStateException(
                "$operation failed (${RtcEngine.getErrorDescription(result)})."
            )
        }
    }

    private fun runRtcBestEffort(
        operation: String,
        result: Int,
    ) {
        if (result != Constants.ERR_OK) {
            Log.w(
                TAG,
                "$operation failed (${RtcEngine.getErrorDescription(result)}), continuing with platform audio routing."
            )
        }
    }
}
