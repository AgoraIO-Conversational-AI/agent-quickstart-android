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

    @Test
    fun remoteSpeakingTransitionsBackToIdleAfterOutputCooldownExpires() {
        val manager = TurnManager { _, _ -> }

        manager.onRemoteAgentState(AgentConversationState.SPEAKING)
        manager.currentStateDurationMs(nowMs = System.currentTimeMillis() + 2_000L)

        assertEquals(TurnState.IDLE, manager.currentState())
        assertTrue(manager.isAsrGateOpen())
    }

    @Test
    fun userSpeechIsIgnoredWhileAgentOutputSuppressionIsActive() {
        val manager = TurnManager { _, _ -> }

        manager.onRemoteAgentState(AgentConversationState.SPEAKING)
        manager.onUserSpeechDetected()

        assertEquals(TurnState.AGENT_SPEAKING, manager.currentState())
        assertFalse(manager.isAsrGateOpen())
    }

    @Test
    fun resetClearsGateAndReturnsToIdle() {
        val manager = TurnManager { _, _ -> }

        manager.onRemoteAgentState(AgentConversationState.THINKING)
        manager.onBargeInDetected()
        manager.reset()

        assertEquals(TurnState.IDLE, manager.currentState())
        assertTrue(manager.isAsrGateOpen())
    }

    @Test
    fun userSpeechResumesDuringFinalizingPhase() {
        val manager = TurnManager { _, _ -> }

        manager.onUserSpeechDetected()
        manager.onUserSpeechEnded()
        assertEquals(TurnState.USER_TURN_FINALIZING, manager.currentState())

        manager.onUserSpeechDetected()

        assertEquals(TurnState.USER_SPEAKING, manager.currentState())
        assertTrue(manager.isAsrGateOpen())
    }

    @Test
    fun agentThinkingTransitionsToIdleAfterOutputCooldownExpires() {
        val manager = TurnManager { _, _ -> }

        manager.onRemoteAgentState(AgentConversationState.THINKING)
        assertEquals(TurnState.AGENT_THINKING, manager.currentState())

        manager.onRemoteAgentState(AgentConversationState.LISTENING)
        manager.currentStateDurationMs(nowMs = System.currentTimeMillis() + 2_000L)

        assertEquals(TurnState.IDLE, manager.currentState())
        assertTrue(manager.isAsrGateOpen())
    }

    @Test
    fun agentThinkingDoesNotOverrideUserTurn() {
        val manager = TurnManager { _, _ -> }

        manager.onUserSpeechDetected()
        manager.onRemoteAgentState(AgentConversationState.THINKING)

        assertEquals(TurnState.USER_SPEAKING, manager.currentState())
    }

    @Test
    fun agentSpeakingDoesNotOverrideUserTurn() {
        val manager = TurnManager { _, _ -> }

        manager.onUserSpeechDetected()
        manager.onRemoteAgentState(AgentConversationState.SPEAKING)

        assertEquals(TurnState.USER_SPEAKING, manager.currentState())
    }

    @Test
    fun agentOutputSuppressionBlocksUserSpeechDuringCooldown() {
        val manager = TurnManager { _, _ -> }

        manager.onAgentOutputActivity()
        manager.onUserSpeechDetected()

        assertEquals(TurnState.IDLE, manager.currentState())
    }

    @Test
    fun agentSpeakingTransitionsToThinkingOnTimeoutWhenRemoteStateIsThinking() {
        val manager = TurnManager { _, _ -> }

        manager.onRemoteAgentState(AgentConversationState.SPEAKING)
        manager.onRemoteAgentState(AgentConversationState.THINKING)

        manager.currentStateDurationMs(nowMs = System.currentTimeMillis() + 3_000L)

        assertEquals(TurnState.AGENT_THINKING, manager.currentState())
    }

    @Test
    fun callbackFiresOnEveryStateTransition() {
        val transitions = mutableListOf<TurnState>()
        val manager = TurnManager { state, _ -> transitions += state }

        manager.onRemoteAgentState(AgentConversationState.SPEAKING)
        manager.onBargeInDetected()
        manager.commitBargeInToUserTurn()

        assertEquals(
            listOf(TurnState.AGENT_SPEAKING, TurnState.BARGE_IN_DETECTED, TurnState.USER_SPEAKING),
            transitions,
        )
    }
}
