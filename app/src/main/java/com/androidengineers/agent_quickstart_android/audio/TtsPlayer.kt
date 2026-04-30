package com.androidengineers.agent_quickstart_android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingDeque

class TtsPlayer(
    private val format: AudioFormatInfo,
    private val log: (String) -> Unit,
    private val onQueueSizeChanged: (Int) -> Unit,
) {
    private val queue = LinkedBlockingDeque<ByteArray>()
    private val playbackLock = Any()
    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(format.sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(format.frameSizeBytes * 8)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()

    @Volatile
    private var shouldPlay: Boolean = false

    private val maxQueuedFrames = 6

    fun enqueue(audioFrame: ByteArray) {
        while (queue.size >= maxQueuedFrames) {
            queue.pollFirst()
            log("tts_queue dropping oldest frame to stay real-time")
        }
        queue.offer(audioFrame)
        onQueueSizeChanged(queue.size)
        log("tts_queue size=${queue.size}")
    }

    fun play() {
        shouldPlay = true
        drainQueue()
    }

    fun playFrame(audioFrame: ByteArray) {
        shouldPlay = true
        synchronized(playbackLock) {
            if (!shouldPlay) {
                return
            }
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.play()
            }
            audioTrack.write(audioFrame, 0, audioFrame.size, AudioTrack.WRITE_BLOCKING)
        }
        onQueueSizeChanged(queue.size)
    }

    fun stopImmediately() {
        shouldPlay = false
        queue.clear()
        onQueueSizeChanged(0)
        synchronized(playbackLock) {
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
        }
        log("tts_stop_immediately queueSize=${queue.size}")
    }

    fun clearQueue() {
        queue.clear()
        onQueueSizeChanged(0)
        log("tts_clear_queue queueSize=${queue.size}")
    }

    fun queueSize(): Int = queue.size

    fun release() {
        shouldPlay = false
        queue.clear()
        synchronized(playbackLock) {
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.release() }
        }
    }

    private fun drainQueue() {
        while (shouldPlay) {
            val frame = queue.poll() ?: break
            playFrame(frame)
        }
    }
}
