package com.androidengineers.agent_quickstart_android.audio

data class VadResult(
    val isSpeech: Boolean,
    val rmsDb: Double,
    val noiseFloorDb: Double,
    val confidence: Double,
)

data class BargeInDecision(
    val detected: Boolean,
    val reason: String,
    val confidence: Double,
)

class BargeInDetector(
    private val frameDurationMs: Int,
    private val log: (String) -> Unit,
) {
    private var consecutiveQualifiedFrames: Int = 0
    private val minimumAgentSpeechBeforeInterruptMs = 600L
    private val unknownPlaybackLevelDb = -80.0

    fun reset() {
        consecutiveQualifiedFrames = 0
    }

    fun evaluate(
        turnState: TurnState,
        vadResult: VadResult,
        playbackLevelDb: Double,
        aecEnabled: Boolean,
        microphoneEnabled: Boolean,
        agentSpeakingDurationMs: Long,
    ): BargeInDecision {
        if (turnState != TurnState.AGENT_SPEAKING) {
            consecutiveQualifiedFrames = 0
            return BargeInDecision(
                detected = false,
                reason = "turn-state-$turnState",
                confidence = 0.0,
            )
        }

        if (!microphoneEnabled) {
            consecutiveQualifiedFrames = 0
            return BargeInDecision(
                detected = false,
                reason = "microphone-muted",
                confidence = 0.0,
            )
        }

        if (agentSpeakingDurationMs < minimumAgentSpeechBeforeInterruptMs) {
            consecutiveQualifiedFrames = 0
            return BargeInDecision(
                detected = false,
                reason = "agent-speaking-too-recently-${agentSpeakingDurationMs}ms",
                confidence = 0.0,
            )
        }

        if (!vadResult.isSpeech) {
            consecutiveQualifiedFrames = 0
            return BargeInDecision(
                detected = false,
                reason = "vad-no-speech",
                confidence = vadResult.confidence,
            )
        }

        val nearEndAdvantageDb = vadResult.rmsDb - playbackLevelDb
        val playbackLevelKnown = playbackLevelDb > unknownPlaybackLevelDb
        val requiredAdvantageDb = if (aecEnabled) 3.5 else 8.0
        val requiredFrames = if (playbackLevelKnown) {
            if (aecEnabled) 8 else 10
        } else {
            if (aecEnabled) 10 else 12
        }
        val playbackIsLoud = playbackLevelDb > -55.0

        if (playbackLevelKnown && playbackIsLoud && nearEndAdvantageDb < requiredAdvantageDb) {
            consecutiveQualifiedFrames = 0
            return BargeInDecision(
                detected = false,
                reason = "likely-speaker-leakage",
                confidence = vadResult.confidence,
            )
        }

        consecutiveQualifiedFrames += 1
        val sustainedSpeechMs = consecutiveQualifiedFrames * frameDurationMs
        val detected = consecutiveQualifiedFrames >= requiredFrames &&
            vadResult.confidence >= (if (playbackLevelKnown) 0.72 else 0.84) &&
            vadResult.rmsDb >= (if (playbackLevelKnown) -34.0 else -28.0)

        val decision = BargeInDecision(
            detected = detected,
            reason = if (detected) {
                "near-end-speech-confirmed-${sustainedSpeechMs}ms"
            } else {
                if (playbackLevelKnown) {
                    "waiting-for-more-speech-${sustainedSpeechMs}ms"
                } else {
                    "waiting-for-strong-local-speech-${sustainedSpeechMs}ms"
                }
            },
            confidence = vadResult.confidence,
        )
        log(
            "barge_in detected=${decision.detected} reason=${decision.reason} " +
            "confidence=${"%.2f".format(decision.confidence)} " +
                "nearEndAdvantageDb=${"%.2f".format(nearEndAdvantageDb)} " +
                "playbackLevelKnown=$playbackLevelKnown " +
                "agentSpeakingDurationMs=$agentSpeakingDurationMs"
        )
        if (detected) {
            consecutiveQualifiedFrames = 0
        }
        return decision
    }
}
