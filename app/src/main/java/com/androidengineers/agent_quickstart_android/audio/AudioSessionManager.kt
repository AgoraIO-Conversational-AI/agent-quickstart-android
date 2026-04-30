package com.androidengineers.agent_quickstart_android.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AudioSessionSnapshot(
    val turnState: TurnState = TurnState.IDLE,
    val audioSourceLabel: String = "uninitialized",
    val aecAvailable: Boolean = false,
    val aecEnabled: Boolean = false,
    val noiseSuppressorEnabled: Boolean = false,
    val ttsQueueSize: Int = 0,
    val lastVadResult: String? = null,
    val lastBargeInEvent: String? = null,
)

class AudioSessionManager(
    context: Context,
    private val onIssue: (source: String, code: String, message: String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val selfSpeechFilter = SelfSpeechFilter()
    private val turnManager = TurnManager(::handleTurnStateChanged)
    private val _snapshot = MutableStateFlow(AudioSessionSnapshot())

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneState: Boolean = false
    private var rtcEngine: RtcEngine? = null

    val snapshot: StateFlow<AudioSessionSnapshot> = _snapshot.asStateFlow()

    fun configureRtcEngine(engine: RtcEngine) {
        rtcEngine = engine
        updateNativeTrackDiagnostics(
            nativeMicTrackActive = true,
            customAudioTrackActive = false,
        )
    }

    fun start() {
        require(rtcEngine != null) {
            "Audio session start requested before the Agora RTC engine was configured."
        }

        selfSpeechFilter.clear()
        turnManager.reset()
        previousAudioMode = audioManager.mode
        previousSpeakerphoneState = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        updateNativeTrackDiagnostics(
            nativeMicTrackActive = true,
            customAudioTrackActive = false,
        )
    }

    fun stop() {
        rtcEngine = null
        restoreAudioRoute()
        selfSpeechFilter.clear()
        turnManager.reset()
        _snapshot.update {
            AudioSessionSnapshot(
                turnState = TurnState.IDLE,
                audioSourceLabel = "Agora native microphone track inactive",
                aecAvailable = it.aecAvailable,
                aecEnabled = it.aecEnabled,
                noiseSuppressorEnabled = it.noiseSuppressorEnabled,
                lastVadResult = "native_mic_track_active=false custom_audio_track_active=false",
            )
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        rtcEngine?.let { engine ->
            val result = engine.muteLocalAudioStream(!enabled)
            if (result != Constants.ERR_OK) {
                onIssue(
                    "audio",
                    "mute-local-audio",
                    "Failed to ${if (enabled) "unmute" else "mute"} the native microphone track " +
                        "(${RtcEngine.getErrorDescription(result)}).",
                )
            }
        }
        updateNativeTrackDiagnostics(
            nativeMicTrackActive = enabled,
            customAudioTrackActive = false,
        )
        log("native_mic_requested_enabled=$enabled")
    }

    fun onAgentStateChanged(state: AgentConversationState) {
        turnManager.onRemoteAgentState(state)
        if (state == AgentConversationState.IDLE || state == AgentConversationState.LISTENING) {
            selfSpeechFilter.clear()
        }
    }

    fun onAssistantTranscript(text: String) {
        selfSpeechFilter.updateCurrentAgentText(text)
        turnManager.onAgentOutputActivity()
    }

    fun onUserTranscriptAccepted(
        interruptingAgent: Boolean,
        isFinal: Boolean,
    ) {
        if (interruptingAgent) {
            turnManager.onBargeInDetected()
            _snapshot.update { current ->
                current.copy(
                    lastBargeInEvent = "detected:user-transcription",
                )
            }
            turnManager.commitBargeInToUserTurn()
        } else {
            turnManager.onUserSpeechDetected()
        }
        if (isFinal) {
            turnManager.onUserSpeechEnded()
        }
    }

    fun shouldAcceptUserTranscript(asrPartial: String): Boolean {
        val currentTurnState = turnManager.currentState()
        if (currentTurnState != TurnState.AGENT_SPEAKING &&
            currentTurnState != TurnState.BARGE_IN_DETECTED &&
            !turnManager.isAgentOutputSuppressionActive()
        ) {
            return true
        }

        val decision = selfSpeechFilter.shouldDiscard(asrPartial)
        val keep = !decision.discard
        if (!keep) {
            _snapshot.update { current ->
                current.copy(
                    lastBargeInEvent = "rejected-self-speech:${decision.reason}"
                )
            }
            log(
                "self_speech_rejected reason=${decision.reason} " +
                    "similarity=${"%.2f".format(decision.similarity)}"
            )
        }
        return keep
    }

    private fun handleTurnStateChanged(
        turnState: TurnState,
        reason: String,
    ) {
        _snapshot.update { current ->
            current.copy(
                turnState = turnState,
                lastBargeInEvent = if (turnState == TurnState.BARGE_IN_DETECTED) {
                    "barge-in-detected:$reason"
                } else {
                    current.lastBargeInEvent
                },
            )
        }
        log("turn_state=$turnState reason=$reason")
    }

    private fun restoreAudioRoute() {
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerphoneState
    }

    private fun updateNativeTrackDiagnostics(
        nativeMicTrackActive: Boolean,
        customAudioTrackActive: Boolean,
    ) {
        _snapshot.update { current ->
            current.copy(
                audioSourceLabel = if (nativeMicTrackActive) {
                    "Agora native microphone track"
                } else {
                    "Agora native microphone track muted"
                },
                lastVadResult = "native_mic_track_active=$nativeMicTrackActive " +
                    "custom_audio_track_active=$customAudioTrackActive",
            )
        }
        log(
            "audio_capture_mode=native_mic " +
                "native_mic_track_active=$nativeMicTrackActive " +
                "custom_audio_track_active=$customAudioTrackActive"
        )
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    companion object {
        private const val TAG = "AudioSessionManager"
    }
}
