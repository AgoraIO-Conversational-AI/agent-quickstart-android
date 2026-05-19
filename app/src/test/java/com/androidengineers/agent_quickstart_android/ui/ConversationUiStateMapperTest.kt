package com.androidengineers.agent_quickstart_android.ui

import com.androidengineers.agent_quickstart_android.audio.TurnState
import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import com.androidengineers.agent_quickstart_android.model.AgentVisualState
import com.androidengineers.agent_quickstart_android.model.ConversationUiState
import com.androidengineers.agent_quickstart_android.model.SessionSnapshot
import com.androidengineers.agent_quickstart_android.model.TranscriptSpeaker
import com.androidengineers.agent_quickstart_android.model.TranscriptTurn
import com.androidengineers.agent_quickstart_android.model.TranscriptTurnStatus
import io.agora.rtc2.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationUiStateMapperTest {
    @Test
    fun `freshUiState carries the default flags`() {
        val state = ConversationUiStateMapper.freshUiState(
            permissionGranted = true,
            warningMessage = "warning",
            errorMessage = "error",
        )

        assertTrue(state.microphonePermissionGranted)
        assertEquals("warning", state.warningMessage)
        assertEquals("error", state.errorMessage)
        assertEquals(AgentVisualState.WAITING, state.agentVisualState)
    }

    @Test
    fun `mergeSession maps live transcript history and labels`() {
        val currentState = ConversationUiState(
            inConversation = true,
            microphonePermissionGranted = true,
        )
        val snapshot = SessionSnapshot(
            channelName = "demo-channel",
            rtcConnectionState = Constants.CONNECTION_STATE_CONNECTED,
            rtmConnectionState = "CONNECTED",
            localRtcUid = 42,
            isAgentRtcConnected = true,
            agentState = AgentConversationState.SPEAKING,
            turnState = TurnState.AGENT_SPEAKING,
            transcriptTurns = listOf(
                TranscriptTurn(
                    key = "user:1:-1",
                    turnId = 1,
                    streamId = null,
                    speaker = TranscriptSpeaker.USER,
                    text = "Hello",
                    status = TranscriptTurnStatus.END,
                    createdAtMillis = 1_000L,
                ),
                TranscriptTurn(
                    key = "agent:2:-1",
                    turnId = 2,
                    streamId = null,
                    speaker = TranscriptSpeaker.AGENT,
                    text = "I am listening",
                    status = TranscriptTurnStatus.IN_PROGRESS,
                    createdAtMillis = 2_000L,
                ),
            ),
            micEnabled = false,
            micRequestedEnabled = false,
            micAutoMuted = true,
        )

        val merged = ConversationUiStateMapper.mergeSession(currentState, snapshot)

        assertEquals("demo-channel", merged.channelName)
        assertEquals("42", merged.localUid)
        assertEquals("RTC connected", merged.rtcConnectionLabel)
        assertEquals("Connected", merged.rtmConnectionLabel)
        assertEquals(AgentVisualState.SPEAKING, merged.agentVisualState)
        assertEquals("Speaking back in real time · barge-in ready", merged.agentStateLabel)
        assertEquals(TurnState.AGENT_SPEAKING, merged.turnState)
        assertFalse(merged.micEnabled)
        assertFalse(merged.micRequestedEnabled)
        assertTrue(merged.micAutoMuted)
        assertEquals(1, merged.transcriptHistory.size)
        assertEquals("Hello", merged.transcriptHistory.first().text)
        assertEquals("I am listening", merged.liveTranscript?.text)
        assertTrue(merged.inConversation)
        assertNull(merged.errorMessage)
    }

    @Test
    fun `mergeSession maps waiting when rtc is connecting`() {
        val merged = ConversationUiStateMapper.mergeSession(
            currentState = ConversationUiState(),
            snapshot = SessionSnapshot(
                rtcConnectionState = Constants.CONNECTION_STATE_CONNECTING,
                rtmConnectionState = "DISCONNECTED",
                agentState = AgentConversationState.IDLE,
            ),
        )

        assertEquals(AgentVisualState.WAITING, merged.agentVisualState)
        assertEquals("Waiting for the cloud agent", merged.agentStateLabel)
        assertEquals("RTC connecting", merged.rtcConnectionLabel)
    }

    @Test
    fun `mergeSession maps disconnected when rtc fails`() {
        val merged = ConversationUiStateMapper.mergeSession(
            currentState = ConversationUiState(inConversation = true),
            snapshot = SessionSnapshot(
                rtcConnectionState = Constants.CONNECTION_STATE_FAILED,
                rtmConnectionState = "FAILED",
                agentState = AgentConversationState.IDLE,
            ),
        )

        assertEquals(AgentVisualState.DISCONNECTED, merged.agentVisualState)
        assertEquals("Connection interrupted", merged.agentStateLabel)
        assertEquals("RTC failed", merged.rtcConnectionLabel)
        assertTrue(merged.inConversation)
    }
}
