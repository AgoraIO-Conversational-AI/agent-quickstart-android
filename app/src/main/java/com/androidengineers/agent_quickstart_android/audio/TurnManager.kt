package com.androidengineers.agent_quickstart_android.audio

import com.androidengineers.agent_quickstart_android.model.AgentConversationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TurnState {
    IDLE,
    USER_SPEAKING,
    USER_TURN_FINALIZING,
    AGENT_THINKING,
    AGENT_SPEAKING,
    BARGE_IN_DETECTED,
}

class TurnManager(
    private val onStateChanged: (TurnState, String) -> Unit,
) {
    private val _state = MutableStateFlow(TurnState.IDLE)
    private var stateChangedAtMs: Long = System.currentTimeMillis()
    private var agentOutputSuppressionUntilMs: Long = 0L
    private val agentOutputCooldownMs: Long = 1_500L
    private var remoteAgentState: AgentConversationState = AgentConversationState.IDLE

    val state: StateFlow<TurnState> = _state.asStateFlow()

    @Volatile
    private var asrGateOpen: Boolean = true

    @Synchronized
    fun reset() {
        asrGateOpen = true
        remoteAgentState = AgentConversationState.IDLE
        agentOutputSuppressionUntilMs = 0L
        transitionTo(TurnState.IDLE, "reset")
    }

    @Synchronized
    fun currentState(): TurnState {
        refreshTimedAgentState(System.currentTimeMillis())
        return _state.value
    }

    @Synchronized
    fun currentStateDurationMs(nowMs: Long = System.currentTimeMillis()): Long {
        refreshTimedAgentState(nowMs)
        return nowMs - stateChangedAtMs
    }

    @Synchronized
    fun isAgentOutputSuppressionActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        refreshTimedAgentState(nowMs)
        if (isUserTurnState(_state.value)) {
            return false
        }
        return nowMs < agentOutputSuppressionUntilMs
    }

    @Synchronized
    fun onAgentOutputActivity(nowMs: Long = System.currentTimeMillis()) {
        agentOutputSuppressionUntilMs = maxOf(
            agentOutputSuppressionUntilMs,
            nowMs + agentOutputCooldownMs,
        )
    }

    @Synchronized
    fun isAgentOutputLive(): Boolean {
        refreshTimedAgentState(System.currentTimeMillis())
        return _state.value == TurnState.AGENT_SPEAKING
    }

    fun isAsrGateOpen(): Boolean = asrGateOpen

    @Synchronized
    fun onRemoteAgentState(state: AgentConversationState) {
        remoteAgentState = state
        when (state) {
            AgentConversationState.SPEAKING -> {
                asrGateOpen = false
                onAgentOutputActivity()
                if (!isUserTurnState(_state.value)) {
                    transitionTo(TurnState.AGENT_SPEAKING, "remote-agent-speaking")
                }
            }

            AgentConversationState.THINKING -> {
                if (!isUserTurnState(_state.value)) {
                    asrGateOpen = false
                    transitionTo(TurnState.AGENT_THINKING, "remote-agent-thinking")
                }
            }

            AgentConversationState.LISTENING,
            AgentConversationState.IDLE,
            AgentConversationState.SILENT,
            AgentConversationState.UNKNOWN,
            -> {
                if (!isUserTurnState(_state.value)) {
                    asrGateOpen = true
                    transitionTo(TurnState.IDLE, "remote-agent-ready")
                }
            }
        }
    }

    @Synchronized
    fun onUserSpeechDetected() {
        if (isAgentOutputSuppressionActive()) {
            return
        }
        when (_state.value) {
            TurnState.USER_SPEAKING -> Unit

            TurnState.USER_TURN_FINALIZING -> {
                asrGateOpen = true
                transitionTo(TurnState.USER_SPEAKING, "local-user-resumed")
            }

            else -> {
                asrGateOpen = true
                transitionTo(TurnState.USER_SPEAKING, "local-user-speech")
            }
        }
    }

    @Synchronized
    fun onUserSpeechEnded() {
        if (_state.value == TurnState.USER_SPEAKING) {
            asrGateOpen = true
            transitionTo(TurnState.USER_TURN_FINALIZING, "local-user-silence")
        }
    }

    @Synchronized
    fun onBargeInDetected() {
        asrGateOpen = false
        transitionTo(TurnState.BARGE_IN_DETECTED, "barge-in-detected")
    }

    @Synchronized
    fun onAgentPlaybackObserved(nowMs: Long = System.currentTimeMillis()) {
        onAgentOutputActivity(nowMs)
    }

    @Synchronized
    fun commitBargeInToUserTurn() {
        asrGateOpen = true
        transitionTo(TurnState.USER_SPEAKING, "barge-in-committed")
    }

    private fun isUserTurnState(state: TurnState): Boolean {
        return state == TurnState.USER_SPEAKING ||
            state == TurnState.USER_TURN_FINALIZING ||
            state == TurnState.BARGE_IN_DETECTED
    }

    @Synchronized
    private fun refreshTimedAgentState(nowMs: Long) {
        if (isUserTurnState(_state.value)) {
            return
        }
        if (nowMs < agentOutputSuppressionUntilMs) {
            return
        }

        when (_state.value) {
            TurnState.AGENT_SPEAKING -> {
                if (remoteAgentState == AgentConversationState.THINKING) {
                    asrGateOpen = false
                    transitionTo(TurnState.AGENT_THINKING, "agent-output-timeout-thinking")
                } else {
                    asrGateOpen = true
                    transitionTo(TurnState.IDLE, "agent-output-timeout-idle")
                }
            }

            TurnState.AGENT_THINKING -> {
                if (remoteAgentState != AgentConversationState.THINKING) {
                    asrGateOpen = true
                    transitionTo(TurnState.IDLE, "agent-thinking-timeout-idle")
                }
            }

            else -> Unit
        }
    }

    private fun transitionTo(
        newState: TurnState,
        reason: String,
    ) {
        if (_state.value == newState) {
            return
        }
        _state.value = newState
        stateChangedAtMs = System.currentTimeMillis()
        onStateChanged(newState, reason)
    }
}
