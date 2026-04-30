package com.androidengineers.agent_quickstart_android.rtc

import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurn
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import org.json.JSONObject

data class TranscriptPayload(
    val objectType: String,
    val turnId: Long? = null,
    val streamId: Long? = null,
    val text: String = "",
    val isFinal: Boolean? = null,
    val turnStatus: Int? = null,
    val sentAtMillis: Long? = null,
)

class TranscriptAssembler {
    private val turns = mutableListOf<TranscriptTurn>()

    fun reset() {
        turns.clear()
    }

    fun handlePayload(
        payload: JSONObject,
        localRtcUid: Int,
    ): List<TranscriptTurn> {
        return handlePayload(
            payload = TranscriptPayload(
                objectType = payload.optString("object"),
                turnId = payload.optionalLong("turn_id"),
                streamId = payload.optionalLong("stream_id"),
                text = payload.optString("text"),
                isFinal = payload.takeIf { payload.has("final") }?.optBoolean("final"),
                turnStatus = payload.optionalInt("turn_status"),
                sentAtMillis = payload.optionalLong("send_ts"),
            ),
            localRtcUid = localRtcUid,
        )
    }

    fun handlePayload(
        payload: TranscriptPayload,
        localRtcUid: Int,
    ): List<TranscriptTurn> {
        when (payload.objectType) {
            "user.transcription" -> upsertTranscription(
                payload = payload,
                speaker = TranscriptSpeaker.USER,
                status = if (payload.isFinal != false) {
                    TranscriptTurnStatus.END
                } else {
                    TranscriptTurnStatus.IN_PROGRESS
                },
                localRtcUid = localRtcUid,
            )

            "assistant.transcription" -> upsertTranscription(
                payload = payload,
                speaker = TranscriptSpeaker.AGENT,
                status = payload.turnStatus.toTurnStatus(),
                localRtcUid = localRtcUid,
            )

            "message.interrupt" -> markInterrupted(payload.turnId)
        }

        return snapshot()
    }

    fun snapshot(): List<TranscriptTurn> {
        return turns.sortedWith(
            compareBy<TranscriptTurn> { it.createdAtMillis }
                .thenBy { it.turnId }
                .thenBy { it.key }
        )
    }

    private fun upsertTranscription(
        payload: TranscriptPayload,
        speaker: TranscriptSpeaker,
        status: TranscriptTurnStatus,
        localRtcUid: Int,
    ) {
        val turnId = payload.turnId ?: System.currentTimeMillis()
        val streamId = payload.streamId
        val speakerKey = if (speaker == TranscriptSpeaker.USER) {
            localRtcUid.toString()
        } else {
            "agent"
        }
        val key = "$speakerKey:$turnId:${streamId ?: -1L}"
        val normalizedText = normalizeTranscriptSpacing(payload.text)
        val createdAt = normalizeTimestampMs(
            payload.sentAtMillis ?: System.currentTimeMillis()
        )
        val existingIndex = turns.indexOfFirst { it.key == key }

        if (existingIndex == -1) {
            turns += TranscriptTurn(
                key = key,
                turnId = turnId,
                streamId = streamId,
                speaker = speaker,
                text = normalizedText,
                status = status,
                createdAtMillis = createdAt,
            )
            return
        }

        val existing = turns[existingIndex]
        turns[existingIndex] = existing.copy(
            text = normalizedText.ifBlank { existing.text },
            status = status,
        )
    }

    private fun markInterrupted(turnId: Long?) {
        val matchingIndex = turns.indexOfLast { turn ->
            turn.speaker == TranscriptSpeaker.AGENT && (
                turnId == null || turn.turnId == turnId
            )
        }
        if (matchingIndex == -1) {
            return
        }
        turns[matchingIndex] = turns[matchingIndex].copy(
            status = TranscriptTurnStatus.INTERRUPTED,
        )
    }

    private fun Int?.toTurnStatus(): TranscriptTurnStatus {
        return when (this) {
            0 -> TranscriptTurnStatus.IN_PROGRESS
            2 -> TranscriptTurnStatus.INTERRUPTED
            else -> TranscriptTurnStatus.END
        }
    }

    private fun JSONObject.optionalLong(key: String): Long? {
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun normalizeTimestampMs(timestamp: Long): Long {
        return if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000L
    }

    private fun normalizeTranscriptSpacing(text: String): String {
        return text
            .replace(Regex("([.!?])([A-Za-z])"), "$1 $2")
            .replace(Regex(",([A-Za-z])"), ", $1")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
