package com.androidengineers.agent_quickstart_android.audio

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

class PlaybackPipeline(
    private val realtimeAgentClient: RealtimeAgentClient,
    private val format: AudioFormatInfo,
    private val log: (String) -> Unit,
    private val onPlaybackLevelChanged: (Double) -> Unit,
    private val onQueueSizeChanged: (Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ttsPlayer = TtsPlayer(
        format = format,
        log = log,
        onQueueSizeChanged = onQueueSizeChanged,
    )

    @Volatile
    private var playbackJob: Job? = null

    fun start() {
        if (playbackJob?.isActive == true) {
            return
        }
        playbackJob = scope.launch {
            val buffer = ByteArray(format.frameSizeBytes)
            while (isActive) {
                val result = realtimeAgentClient.pullPlaybackAudio(
                    target = buffer,
                    expectedSizeBytes = format.frameSizeBytes,
                )
                if (result < 0) {
                    log("playback_pull failed code=$result")
                    onPlaybackLevelChanged(-90.0)
                    delay(format.frameDurationMs.toLong())
                    continue
                }

                if (result == 0) {
                    onPlaybackLevelChanged(-90.0)
                    delay(format.frameDurationMs.toLong())
                    continue
                }

                val size = result.coerceAtMost(format.frameSizeBytes)
                val audioBytes = if (size == buffer.size) {
                    buffer.copyOf()
                } else {
                    buffer.copyOf(size)
                }
                val playbackLevelDb = calculateRmsDb(audioBytes)
                onPlaybackLevelChanged(playbackLevelDb)

                if (playbackLevelDb > -68.0) {
                    ttsPlayer.playFrame(audioBytes)
                }
            }
        }
    }

    fun stopImmediately() {
        ttsPlayer.stopImmediately()
    }

    fun clearQueue() {
        ttsPlayer.clearQueue()
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        ttsPlayer.release()
        scope.cancel()
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
}
