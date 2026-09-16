package com.androidengineers.agent_quickstart_android.rtc

import android.content.Context
import android.util.Base64
import android.util.Log
import com.androidengineers.agent_quickstart_android.audio.AudioSessionManager
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
import io.agora.rtc2.video.CameraCapturerConfiguration
import io.agora.rtc2.video.VideoEncoderConfiguration
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
import java.io.File
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
import org.json.JSONObject

class AgoraConversationSessionManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
    private val renewMutex = Mutex()
    private val transcriptAssembler = TranscriptAssembler()
    private val repository = ConversationRepository()
    private val audioSessionManager = AudioSessionManager(
        context = appContext,
    ) { source, code, message ->
        addIssue(source = source, code = code, message = message)
    }
    private val _snapshot = MutableStateFlow(SessionSnapshot())
    private val _rtcEngineState = MutableStateFlow<RtcEngine?>(null)

    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()
    val rtcEngineState: StateFlow<RtcEngine?> = _rtcEngineState.asStateFlow()

    private var rtcEngine: RtcEngine? = null
    private var rtmClient: RtmClient? = null
    private var currentChannel: String? = null
    private var localRtcUid: Int = 0
    private var currentAgentRtcUid: Int = 0
    private var micRequestedEnabled: Boolean = true
    private var renewTokensProvider: (suspend (String, Int, String) -> RenewalTokens)? = null
    private var joinDeferred: CompletableDeferred<Int>? = null
    private var snapshotDeferred: CompletableDeferred<File>? = null
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
        currentAgentRtcUid = bootstrap.agentRtcUid
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
            ensureRtcEngine(bootstrap.appId)
            ensureRtmClient(
                appId = bootstrap.appId,
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
        currentAgentRtcUid = 0
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

        rtcEngine?.let { engine ->
            runCatching { engine.stopPreview() }
            runCatching { engine.leaveChannel() }
        }
        rtcEngine = null
        _rtcEngineState.value = null
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


    suspend fun captureLocalCameraFrameBase64(): String {
        val engine = rtcEngine ?: throw IOException("RTC engine is not initialized.")
        if (currentChannel == null || localRtcUid == 0) {
            throw IOException("RTC channel is not ready for camera capture.")
        }
        if (snapshotDeferred != null) {
            throw IOException("A camera frame is already being analyzed.")
        }
        val snapshotFile = File(
            appContext.cacheDir,
            "live_lens_snapshot_${System.currentTimeMillis()}.jpg",
        )
        val deferred = CompletableDeferred<File>()
        snapshotDeferred = deferred
        val result = withContext(Dispatchers.Main.immediate) {
            engine.takeSnapshot(0, snapshotFile.absolutePath)
        }
        if (result != Constants.ERR_OK) {
            snapshotDeferred = null
            throw IOException("Camera snapshot failed (${RtcEngine.getErrorDescription(result)}).")
        }
        return try {
            val file = withTimeout(5_000L) { deferred.await() }
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } catch (error: TimeoutCancellationException) {
            if (snapshotDeferred == deferred) {
                snapshotDeferred = null
            }
            throw IOException("Camera snapshot timed out.", error)
        } finally {
            snapshotFile.delete()
        }
    }

    fun setActiveAgentId(agentId: String?) {
        activeAgentId = agentId
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
            mAudioScenario = Constants.AUDIO_SCENARIO_CHORUS
        }

        val engine = RtcEngine.create(config)
            ?: throw IllegalStateException(
                "Agora RTC engine failed to initialize. Verify AGORA_APP_ID and required Android network/audio permissions."
            )

        checkRtcResult(
            operation = "enableAudio",
            result = engine.enableAudio(),
        )
        checkRtcResult(
            operation = "enableVideo",
            result = engine.enableVideo(),
        )
        runRtcBestEffort(
            operation = "startRearCameraCapture",
            result = engine.startCameraCapture(
                Constants.VideoSourceType.VIDEO_SOURCE_CAMERA_PRIMARY,
                CameraCapturerConfiguration(CameraCapturerConfiguration.CAMERA_DIRECTION.CAMERA_REAR),
            ),
        )
        runRtcBestEffort(
            operation = "setVideoEncoderConfiguration",
            result = engine.setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x480,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE,
                )
            ),
        )
        runRtcBestEffort(
            operation = "setDefaultAudioRoutetoSpeakerphone",
            result = engine.setDefaultAudioRoutetoSpeakerphone(true),
        )
        runRtcBestEffort(
            operation = "setAudioProfile",
            result = engine.setAudioProfile(
                Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                Constants.AUDIO_SCENARIO_CHORUS,
            ),
        )
        audioSessionManager.configureRtcEngine(engine)
        rtcEngine = engine
        _rtcEngineState.value = engine
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
            val subscribeOptions = SubscribeOptions().apply {
                setWithMessage(true)
                setWithPresence(true)
            }
            awaitRtmVoid { callback -> client.subscribe(channel, subscribeOptions, callback) }
            rtmClient = client
        } catch (error: Throwable) {
            runCatching { client.removeEventListener(rtmEventListener) }
            runCatching { client.logout(noopRtmCallback()) }
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
                    publishCameraTrack = true
                    publishCustomAudioTrack = false
                    autoSubscribeAudio = true
                    autoSubscribeVideo = false
                    enableAudioRecordingOrPlayout = true
                    startPreview = true
                }
            )
        }

        Log.i(
            TAG,
            "rtc_publish_config native_mic_track_active=true camera_track_active=true custom_audio_track_active=false"
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

                if (audioSessionManager.shouldAcceptUserTranscript(text)) {
                    val isFinal = payload.takeIf { payload.has("final") }?.optBoolean("final") != false

                    audioSessionManager.onUserTranscriptAccepted(
                        interruptingAgent = agentSpeaking,
                        isFinal = isFinal,
                    )

                    if (agentSpeaking) {
                        requestAgentInterruptFromUserSpeech(text)
                    }

                    updateTranscript(payload)
                } else {
                    Log.i(TAG, "Discarded self-speech transcript.")
                }
            }

            "assistant.transcription" -> {
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
        return when (lowercase(Locale.ROOT)) {
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
        val channelName = currentChannel ?: return
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
                repository.interruptConversation(
                    agentId = agentId,
                    channelName = channelName,
                )
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


        override fun onSnapshotTaken(uid: Int, filePath: String, width: Int, height: Int, errCode: Int) {
            val deferred = snapshotDeferred ?: return
            snapshotDeferred = null
            if (errCode == Constants.ERR_OK) {
                deferred.complete(File(filePath))
            } else {
                deferred.completeExceptionally(
                    IOException("Camera snapshot failed (${RtcEngine.getErrorDescription(errCode)}).")
                )
            }
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
                    message = "RTM link ${event.getCurrentState().name.lowercase(Locale.ROOT)} (${event.getReasonCode().name}).",
                )

                else -> Unit
            }
        }
    }

    companion object {
        private const val TAG = "AgoraConversationSession"
        private const val RTC_JOIN_TIMEOUT_MS = 20_000L
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
