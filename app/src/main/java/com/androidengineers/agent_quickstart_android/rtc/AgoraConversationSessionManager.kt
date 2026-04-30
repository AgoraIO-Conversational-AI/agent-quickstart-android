package com.androidengineers.agent_quickstart_android.rtc

import android.content.Context
import android.util.Log
import com.androidengineers.agent_quickstart_android.audio.AudioSessionManager
import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.data.ConversationRepository
import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.RenewalTokens
import com.androidengineers.agent_quickstart_android.model.SessionIssue
import com.androidengineers.agent_quickstart_android.model.SessionSnapshot
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtm.ErrorInfo
import io.agora.rtm.LinkStateEvent
import io.agora.rtm.MessageEvent
import io.agora.rtm.PresenceEvent
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmConfig
import io.agora.rtm.RtmConstants
import io.agora.rtm.RtmEventListener
import io.agora.rtm.SubscribeOptions
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.json.JSONObject

class AgoraConversationSessionManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val renewMutex = Mutex()
    private val transcriptAssembler = TranscriptAssembler()
    private val backendRepository = ConversationRepository()
    private val audioSessionManager = AudioSessionManager(
        context = appContext,
    ) { source, code, message ->
        addIssue(source = source, code = code, message = message)
    }
    private val _snapshot = MutableStateFlow(SessionSnapshot())

    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    private var rtcEngine: RtcEngine? = null
    private var rtmClient: RtmClient? = null
    private var currentChannel: String? = null
    private var localRtcUid: Int = 0
    private var micRequestedEnabled: Boolean = true
    private var renewTokensProvider: (suspend (String, Int, String) -> RenewalTokens)? = null
    private var joinDeferred: CompletableDeferred<Int>? = null
    private var activeAgentId: String? = null
    private var currentRtmUserId: String? = null
    private var currentAgentTurnId: Long? = null
    private var interruptRequestedTurnId: Long? = null
    private var lastInterruptRequestAtMs: Long = 0L

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
        renewTokensProvider = onRenewTokens
        transcriptAssembler.reset()
        micRequestedEnabled = true
        activeAgentId = null
        currentRtmUserId = bootstrap.rtmUserId
        currentAgentTurnId = null
        interruptRequestedTurnId = null
        lastInterruptRequestAtMs = 0L
        _snapshot.value = SessionSnapshot(
            channelName = bootstrap.channel,
            micEnabled = currentMicEnabled(),
            micRequestedEnabled = micRequestedEnabled,
            micAutoMuted = false,
        )

        try {
            ensureRtcEngine()
            ensureRtmClient(
                token = bootstrap.rtmToken,
                channel = bootstrap.channel,
                userId = bootstrap.rtmUserId,
            )
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
        micRequestedEnabled = true
        activeAgentId = null
        currentRtmUserId = null
        currentAgentTurnId = null
        interruptRequestedTurnId = null
        lastInterruptRequestAtMs = 0L
        audioSessionManager.stop()

        val channel = currentChannel
        currentChannel = null

        rtmClient?.let { client ->
            runCatching { client.removeEventListener(rtmEventListener) }
            if (!channel.isNullOrBlank()) {
                runCatching {
                    client.unsubscribe(channel, noopRtmCallback())
                }
            }
            runCatching { client.logout(noopRtmCallback()) }
        }
        rtmClient = null
        runCatching { RtmClient.release() }

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
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        micRequestedEnabled = enabled
        audioSessionManager.setMicrophoneEnabled(enabled)
        syncMicState()
    }

    fun setActiveAgentId(agentId: String?) {
        activeAgentId = agentId
    }

    private suspend fun ensureRtcEngine() = withContext(Dispatchers.Main.immediate) {
        if (rtcEngine != null) {
            return@withContext
        }

        val config = RtcEngineConfig().apply {
            mContext = appContext
            mAppId = QuickstartConfig.agoraAppId
            mEventHandler = rtcEventHandler
            mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            mAudioScenario = Constants.AUDIO_SCENARIO_CHATROOM
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
                Constants.AUDIO_SCENARIO_CHATROOM,
            ),
        )
        audioSessionManager.configureRtcEngine(engine)
        rtcEngine = engine
    }

    private suspend fun ensureRtmClient(
        token: String,
        channel: String,
        userId: String,
    ) {
        val client = RtmClient.create(
            RtmConfig.Builder(QuickstartConfig.agoraAppId, userId)
                .useStringUserId(true)
                .build()
        )
        client.addEventListener(rtmEventListener)

        try {
            awaitRtmVoid { callback -> client.login(token, callback) }
            val subscribeOptions = SubscribeOptions().apply {
                setWithMessage(true)
                setWithPresence(true)
            }
            awaitRtmVoid { callback -> client.subscribe(channel, subscribeOptions, callback) }
            rtmClient = client
        } catch (error: Throwable) {
            runCatching { client.removeEventListener(rtmEventListener) }
            runCatching { client.logout(noopRtmCallback()) }
            runCatching { RtmClient.release() }
            throw error
        }
    }

    private suspend fun joinRtcChannel(bootstrap: AgoraTokenBundle) {
        val engine = rtcEngine ?: throw IllegalStateException("RTC engine is not initialized.")
        val requestedUid = bootstrap.uid.toIntOrNull() ?: 0
        val deferred = CompletableDeferred<Int>()
        joinDeferred = deferred

        val result = withContext(Dispatchers.Main.immediate) {
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

        deferred.await()
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

    private fun updateTranscript(payload: JSONObject) {
        updateSnapshot { current ->
            current.copy(
                transcriptTurns = transcriptAssembler.handlePayload(
                    payload = payload,
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
                audioSessionManager.setMicrophoneEnabled(false)
                Log.i(TAG, "Auto-muted mic while agent speaking")
            }

            AgentConversationState.LISTENING,
            AgentConversationState.IDLE,
            AgentConversationState.SILENT -> {
                scope.launch {
                    kotlinx.coroutines.delay(800)
                    if (_snapshot.value.agentState != AgentConversationState.SPEAKING) {
                        audioSessionManager.setMicrophoneEnabled(micRequestedEnabled)
                        Log.i(TAG, "Restored mic after 800ms agent tail cooldown")
                    }
                }
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
                        message = error.message ?: "Failed to renew Agora tokens.",
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

    private fun handleRtmMessage(event: MessageEvent) {
        val rawPayload = when (val data = event.getMessage().getData()) {
            is String -> data
            is ByteArray -> data.toString(Charsets.UTF_8)
            else -> data?.toString()
        } ?: return

        val payload = runCatching { JSONObject(rawPayload) }.getOrNull() ?: return
        when (payload.optString("object")) {
            "user.transcription" -> {
                val text = payload.optString("text")
                val agentSpeaking = _snapshot.value.agentState == AgentConversationState.SPEAKING

                if (agentSpeaking) {
                    Log.i(TAG, "Rejected user.transcription during agent speaking: possible AI echo text=$text")
                    return
                }

                if (audioSessionManager.shouldAcceptUserTranscript(text)) {
                    val isFinal = payload.takeIf { payload.has("final") }?.optBoolean("final") != false

                    audioSessionManager.onUserTranscriptAccepted(
                        interruptingAgent = false,
                        isFinal = isFinal,
                    )

                    updateTranscript(payload)
                } else {
                    Log.i(TAG, "Discarded self-speech transcript.")
                }
            }

            "assistant.transcription" -> {
                audioSessionManager.setMicrophoneEnabled(false)
                audioSessionManager.onAssistantTranscript(payload.optString("text"))
                updateTranscript(payload)
            }

            "message.interrupt" -> updateTranscript(payload)

            "message.state" -> updateAgentState(
                rawState = payload.optString("state"),
                turnId = payload.optionalLong("turn_id"),
                timestampMillis = normalizeTimestampMs(
                    payload.optionalLong("send_ts") ?: System.currentTimeMillis()
                ),
            )

            "message.error" -> addIssue(
                source = "rtm-signaling",
                code = payload.opt("code")?.toString() ?: "unknown",
                message = "${payload.optString("module").ifBlank { "unknown" }}: " +
                    payload.optString("message").ifBlank { "Unknown signaling error." },
                timestampMillis = normalizeTimestampMs(
                    payload.optionalLong("send_ts") ?: System.currentTimeMillis()
                ),
            )

            "message.sal_status" -> addIssue(
                source = "rtm-signaling",
                code = payload.optString("status").ifBlank { "unknown" },
                message = "SAL status: ${payload.optString("status").ifBlank { "unknown" }}",
                timestampMillis = normalizeTimestampMs(
                    payload.optionalLong("timestamp") ?: System.currentTimeMillis()
                ),
            )
        }
    }

    private fun handlePresenceEvent(event: PresenceEvent) {
        val stateItems = event.getStateItems()
        val state = stateItems["state"] ?: return
        val turnId = stateItems["turn_id"]?.toLongOrNull()
        updateAgentState(
            rawState = state,
            turnId = turnId,
            timestampMillis = normalizeTimestampMs(event.getTimestamp()),
        )
    }

    private fun String.toAgentConversationState(): AgentConversationState {
        return when (lowercase()) {
            "idle" -> AgentConversationState.IDLE
            "listening" -> AgentConversationState.LISTENING
            "thinking" -> AgentConversationState.THINKING
            "speaking" -> AgentConversationState.SPEAKING
            "silent" -> AgentConversationState.SILENT
            else -> AgentConversationState.UNKNOWN
        }
    }

    private fun normalizeTimestampMs(timestamp: Long): Long {
        return if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000L
    }

    private fun JSONObject.optionalLong(key: String): Long? {
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun requestAgentInterruptFromUserSpeech(text: String) {
        if (text.isBlank()) {
            return
        }
        val agentId = activeAgentId ?: return
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
                backendRepository.interruptConversation(agentId)
            }.onSuccess {
                Log.i(
                    TAG,
                    "interrupt_event_sent agentId=$agentId reason=user-transcription turnId=${turnId ?: "none"}"
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
            if (uid == QuickstartConfig.agentUid) {
                updateSnapshot { it.copy(isAgentRtcConnected = true) }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            if (uid == QuickstartConfig.agentUid) {
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
        override fun onMessageEvent(event: MessageEvent) {
            handleRtmMessage(event)
        }

        override fun onPresenceEvent(event: PresenceEvent) {
            handlePresenceEvent(event)
        }

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
                    message = "RTM link ${event.getCurrentState().name.lowercase()} (${event.getReasonCode().name}).",
                )

                else -> Unit
            }
        }
    }

    companion object {
        private const val TAG = "AgoraConversationSession"
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
