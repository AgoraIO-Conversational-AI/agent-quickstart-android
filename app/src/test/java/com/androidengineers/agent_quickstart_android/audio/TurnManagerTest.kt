package com.androidengineers.agent_quickstart_android.audio

import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnManagerTest {
    @Test
    fun entersAgentSpeakingAndClosesAsrGate() {
        val manager = TurnManager { _, _ -> }

        manager.onRemoteAgentState(AgentConversationState.SPEAKING)

        assertEquals(TurnState.AGENT_SPEAKING, manager.currentState())
        assertFalse(manager.isAsrGateOpen())
    }

    @Test
    fun commitsBargeInBackToUserSpeaking() {
        val manager = TurnManager { _, _ -> }
        manager.onRemoteAgentState(AgentConversationState.SPEAKING)

        manager.onBargeInDetected()
        manager.commitBargeInToUserTurn()

        assertEquals(TurnState.USER_SPEAKING, manager.currentState())
        assertTrue(manager.isAsrGateOpen())
    }

    @Test
    fun finalizesAfterUserSilence() {
        val manager = TurnManager { _, _ -> }

        manager.onUserSpeechDetected()
        manager.onUserSpeechEnded()

        assertEquals(TurnState.USER_TURN_FINALIZING, manager.currentState())
        assertTrue(manager.isAsrGateOpen())
    }
}
