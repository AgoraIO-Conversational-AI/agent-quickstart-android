package com.androidengineers.agent_quickstart_android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CaptureDiagnostics(
    val audioSourceLabel: String,
    val aecAvailable: Boolean,
    val aecEnabled: Boolean,
    val noiseSuppressorEnabled: Boolean,
)

class CapturePipeline(
    private val format: AudioFormatInfo,
    private val turnManager: TurnManager,
    private val bargeInDetector: BargeInDetector,
    private val publishFrame: (ByteArray) -> Boolean,
    private val onVadResult: (VadResult) -> Unit,
    private val onDiagnosticsReady: (CaptureDiagnostics) -> Unit,
    private val onBargeInDetected: (BargeInDecision, List<ByteArray>) -> Unit,
    private val log: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preRollFrames = ArrayDeque<ByteArray>()
    private val silenceFrame = ByteArray(format.frameSizeBytes)

    private var audioRecord: AudioRecord? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureJob: Job? = null
    private var microphoneEnabled: Boolean = true
    private var playbackLevelDb: Double = -90.0
    private var speechActive: Boolean = false
    private var silenceFrames: Int = 0
    private var noiseFloorDb: Double = -72.0
    private var aecEnabled: Boolean = false

    fun start() {
        if (captureJob?.isActive == true) {
            return
        }

        val captureSetup = createAudioRecord()
        audioRecord = captureSetup.audioRecord
        acousticEchoCanceler = captureSetup.acousticEchoCanceler
        noiseSuppressor = captureSetup.noiseSuppressor
        aecEnabled = captureSetup.aecEnabled
        onDiagnosticsReady(
            CaptureDiagnostics(
                audioSourceLabel = captureSetup.audioSourceLabel,
                aecAvailable = captureSetup.aecAvailable,
                aecEnabled = captureSetup.aecEnabled,
                noiseSuppressorEnabled = captureSetup.noiseSuppressorEnabled,
            )
        )
        log(
            "capture_source=${captureSetup.audioSourceLabel} " +
                "aecAvailable=${captureSetup.aecAvailable} " +
                "aecEnabled=${captureSetup.aecEnabled} " +
                "noiseSuppressorEnabled=${captureSetup.noiseSuppressorEnabled}"
        )

        captureJob = scope.launch {
            val record = audioRecord ?: return@launch
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord failed to enter RECORDSTATE_RECORDING.")
            }

            val buffer = ByteArray(format.frameSizeBytes)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    log("capture_read failed code=$read")
                    delay(format.frameDurationMs.toLong())
                    continue
                }

                val bytes = if (read == buffer.size) {
                    buffer.copyOf()
                } else {
                    buffer.copyOf(read)
                }

                val vadResult = analyzeVad(bytes)
                onVadResult(vadResult)

                val turnState = turnManager.currentState()
                val suppressForAgentOutput = turnManager.isAgentOutputSuppressionActive()
                if (suppressForAgentOutput) {
                    speechActive = false
                    silenceFrames = 0
                    rememberPreRoll(bytes)
                    if (turnManager.isAgentOutputLive()) {
                        val decision = bargeInDetector.evaluate(
                            turnState = TurnState.AGENT_SPEAKING,
                            vadResult = vadResult,
                            playbackLevelDb = playbackLevelDb,
                            aecEnabled = aecEnabled,
                            microphoneEnabled = microphoneEnabled,
                            agentSpeakingDurationMs = if (turnState == TurnState.AGENT_SPEAKING) {
                                turnManager.currentStateDurationMs()
                            } else {
                                601L
                            },
                        )
                        if (decision.detected) {
                            val bufferedFrames = preRollFrames.toList()
                            preRollFrames.clear()
                            onBargeInDetected(decision, bufferedFrames)
                            continue
                        }
                    } else {
                        bargeInDetector.reset()
                    }
                    log("capture_gate=silence reason=agent-output-suppression turnState=$turnState")
                    pushFrameToRealtime(silenceFrame)
                } else {
                    preRollFrames.clear()
                    bargeInDetector.reset()
                    processSpeechTransitions(vadResult)
                    val frameToPublish = if (microphoneEnabled) {
                        bytes
                    } else {
                        silenceFrame
                    }
                    pushFrameToRealtime(frameToPublish)
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { acousticEchoCanceler?.release() }
        acousticEchoCanceler = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        preRollFrames.clear()
        scope.cancel()
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneEnabled = enabled
    }

    fun updatePlaybackLevelDb(levelDb: Double) {
        playbackLevelDb = levelDb
    }

    private fun pushFrameToRealtime(frame: ByteArray) {
        if (!publishFrame(frame)) {
            log("capture_publish failed size=${frame.size}")
        }
    }

    private fun rememberPreRoll(frame: ByteArray) {
        preRollFrames.addLast(frame.copyOf())
        while (preRollFrames.size > 15) {
            preRollFrames.removeFirst()
        }
    }

    private fun processSpeechTransitions(vadResult: VadResult) {
        if (turnManager.isAgentOutputSuppressionActive()) {
            return
        }

        if (vadResult.isSpeech) {
            silenceFrames = 0
            if (!speechActive) {
                speechActive = true
                turnManager.onUserSpeechDetected()
            }
        } else if (speechActive) {
            silenceFrames += 1
            if (silenceFrames >= 8) {
                speechActive = false
                silenceFrames = 0
                turnManager.onUserSpeechEnded()
            }
        }
    }

    private fun analyzeVad(frame: ByteArray): VadResult {
        val rmsDb = calculateRmsDb(frame)
        if (rmsDb < noiseFloorDb + 3.0) {
            noiseFloorDb = (noiseFloorDb * 0.97) + (rmsDb * 0.03)
        }
        val snrDb = rmsDb - noiseFloorDb
        val isSpeech = snrDb > 9.5 || rmsDb > -38.0
        val confidence = ((snrDb - 6.0) / 12.0).coerceIn(0.0, 1.0)
        return VadResult(
            isSpeech = isSpeech,
            rmsDb = rmsDb,
            noiseFloorDb = noiseFloorDb,
            confidence = confidence,
        )
    }

    private fun calculateRmsDb(frame: ByteArray): Double {
        if (frame.isEmpty()) {
            return -90.0
        }

        var sumSquares = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < frame.size) {
            val sample = ((frame[index + 1].toInt() shl 8) or
                (frame[index].toInt() and 0xFF)).toShort().toInt()
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            samples += 1
            index += 2
        }

        if (samples == 0) {
            return -90.0
        }

        val rms = sqrt(sumSquares / samples)
        return if (rms <= 0.0) -90.0 else 20.0 * log10(rms)
    }

    private fun createAudioRecord(): AudioRecordSetup {
        val preferred = runCatching {
            buildAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }.getOrNull()
        if (preferred != null) {
            return preferred
        }

        log("capture_source=VOICE_COMMUNICATION failed, falling back to MIC")
        return buildAudioRecord(MediaRecorder.AudioSource.MIC)
    }

    private fun buildAudioRecord(audioSource: Int): AudioRecordSetup {
        val minBufferSize = AudioRecord.getMinBufferSize(
            format.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) {
            "AudioRecord.getMinBufferSize returned $minBufferSize for source=$audioSource"
        }

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufferSize, format.frameSizeBytes * 6))
            .build()

        require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            "AudioRecord failed to initialize for source=$audioSource"
        }

        val aecAvailable = AcousticEchoCanceler.isAvailable()
        val acousticEchoCanceler = if (aecAvailable) {
            AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply {
                enabled = true
            }
        } else {
            null
        }
        val aecEnabled = acousticEchoCanceler?.enabled == true

        val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.audioSessionId)?.apply {
                enabled = true
            }
        } else {
            null
        }
        val noiseSuppressorEnabled = noiseSuppressor?.enabled == true

        return AudioRecordSetup(
            audioRecord = audioRecord,
            acousticEchoCanceler = acousticEchoCanceler,
            noiseSuppressor = noiseSuppressor,
            audioSourceLabel = if (audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
                "VOICE_COMMUNICATION"
            } else {
                "MIC"
            },
            aecAvailable = aecAvailable,
            aecEnabled = aecEnabled,
            noiseSuppressorEnabled = noiseSuppressorEnabled,
        )
    }

    private data class AudioRecordSetup(
        val audioRecord: AudioRecord,
        val acousticEchoCanceler: AcousticEchoCanceler?,
        val noiseSuppressor: NoiseSuppressor?,
        val audioSourceLabel: String,
        val aecAvailable: Boolean,
        val aecEnabled: Boolean,
        val noiseSuppressorEnabled: Boolean,
    )
}
