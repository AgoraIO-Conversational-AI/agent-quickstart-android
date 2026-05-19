package com.androidengineers.agent_quickstart_android

import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import com.androidengineers.agent_quickstart_android.rtc.TranscriptPayload
import com.androidengineers.agent_quickstart_android.rtc.TranscriptAssembler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun assistantTranscriptUpdatesTheExistingTurnInsteadOfDuplicatingIt() {
        val assembler = TranscriptAssembler()

        assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "assistant.transcription",
                turnId = 11,
                streamId = 3,
                text = "Let me think",
                turnStatus = 0,
                sentAtMillis = 1714310400000,
            ),
            localRtcUid = 24,
        )

        val snapshot = assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "assistant.transcription",
                turnId = 11,
                streamId = 3,
                text = "Let me think this through.",
                turnStatus = 1,
                sentAtMillis = 1714310401000,
            ),
            localRtcUid = 24,
        )

        assertEquals(1, snapshot.size)
        assertEquals("Let me think this through.", snapshot.first().text)
        assertEquals(TranscriptTurnStatus.END, snapshot.first().status)
    }

    @Test
    fun snapshotOrdersTurnsByTimestampThenTurnId() {
        val assembler = TranscriptAssembler()

        assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "assistant.transcription",
                turnId = 30,
                streamId = 1,
                text = "second",
                turnStatus = 1,
                sentAtMillis = 1714310402000,
            ),
            localRtcUid = 24,
        )
        assembler.handlePayload(
            payload = TranscriptPayload(
                objectType = "user.transcription",
                turnId = 10,
                streamId = 1,
                text = "first",
                isFinal = true,
                sentAtMillis = 1714310400000,
            ),
            localRtcUid = 24,
        )

        val snapshot = assembler.snapshot()

        assertEquals(2, snapshot.size)
        assertEquals(10, snapshot[0].turnId)
        assertEquals(30, snapshot[1].turnId)
    }

    @Test
    fun jsonPayloadParsingNormalizesSpacingAndSecondBasedTimestamps() {
        val assembler = TranscriptAssembler()

        val payload = JSONObject().apply {
            put("object", "user.transcription")
            put("turn_id", "15")
            put("stream_id", "9")
            put("text", "Hello.world,again")
            put("final", false)
            put("send_ts", 1714310400)
        }

        val snapshot = assembler.handlePayload(
            payload = payload,
            localRtcUid = 77,
        )

        assertEquals(1, snapshot.size)
        assertEquals("Hello. world, again", snapshot.first().text)
        assertEquals(1714310400000, snapshot.first().createdAtMillis)
        assertEquals(TranscriptTurnStatus.IN_PROGRESS, snapshot.first().status)
        assertTrue(snapshot.first().key.startsWith("77:15:9"))
    }
}
