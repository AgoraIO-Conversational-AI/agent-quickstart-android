package com.androidengineers.agent_quickstart_android.audio

import java.nio.ByteBuffer

data class AudioFormatInfo(
    val sampleRateHz: Int = 16_000,
    val channelCount: Int = 1,
    val bytesPerSample: Int = 2,
    val frameDurationMs: Int = 20,
) {
    val samplesPerChannel: Int
        get() = sampleRateHz * frameDurationMs / 1000

    val frameSizeBytes: Int
        get() = samplesPerChannel * channelCount * bytesPerSample
}

interface RealtimeAgentClient {
    fun pushCapturedAudio(
        frame: ByteBuffer,
        format: AudioFormatInfo,
        timestampMs: Long,
    ): Boolean

    fun pullPlaybackAudio(
        target: ByteArray,
        expectedSizeBytes: Int,
    ): Int

    suspend fun sendInterrupt(reason: String)
}
