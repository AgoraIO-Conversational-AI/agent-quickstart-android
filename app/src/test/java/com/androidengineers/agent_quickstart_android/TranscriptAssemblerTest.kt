package com.androidengineers.agent_quickstart_android

import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import com.androidengineers.agent_quickstart_android.rtc.TranscriptPayload
import com.androidengineers.agent_quickstart_android.rtc.TranscriptAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TranscriptAssemblerTest {
    @Test
    fun userTranscriptTransitionsFromInProgressToEnd() {
        val assembler = TranscriptAssembler()

        assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "user.transcription",
                turnId = 7,
                streamId = 1,
                text = "hello there",
                isFinal = false,
                sentAtMillis = 1714310400000,
            ),
            localRtcUid = 42,
        )

        val finalSnapshot = assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "user.transcription",
                turnId = 7,
                streamId = 1,
                text = "hello there",
                isFinal = true,
                sentAtMillis = 1714310401000,
            ),
            localRtcUid = 42,
        )

        assertEquals(1, finalSnapshot.size)
        assertEquals(TranscriptSpeaker.USER, finalSnapshot.first().speaker)
        assertEquals(TranscriptTurnStatus.END, finalSnapshot.first().status)
    }

    @Test
    fun interruptMarksAgentTurnAsInterrupted() {
        val assembler = TranscriptAssembler()

        assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "assistant.transcription",
                turnId = 9,
                streamId = 5,
                text = "Let me think through that",
                turnStatus = 0,
                sentAtMillis = 1714310400000,
            ),
            localRtcUid = 24,
        )

        val interruptedSnapshot = assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "message.interrupt",
                turnId = 9,
            ),
            localRtcUid = 24,
        )

        val interruptedTurn = interruptedSnapshot.firstOrNull()
        assertNotNull(interruptedTurn)
        assertEquals(TranscriptTurnStatus.INTERRUPTED, interruptedTurn?.status)
    }
}
