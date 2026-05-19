package com.androidengineers.agent_quickstart_android.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfSpeechFilterTest {
    @Test
    fun rejectsTranscriptThatMatchesAgentSpeech() {
        val filter = SelfSpeechFilter()
        filter.updateCurrentAgentText("The weather tomorrow in San Francisco will be sunny and mild.")

        val decision = filter.shouldDiscard("tomorrow in san francisco will be sunny and mild")

        assertTrue(decision.discard)
    }

    @Test
    fun keepsProtectedInterruptCommands() {
        val filter = SelfSpeechFilter()
        filter.updateCurrentAgentText("I can help you schedule that for tomorrow afternoon.")

        val decision = filter.shouldDiscard("stop")

        assertFalse(decision.discard)
    }

    @Test
    fun keepsUnrelatedUserSpeech() {
        val filter = SelfSpeechFilter()
        filter.updateCurrentAgentText("Here are three travel options for next week.")

        val decision = filter.shouldDiscard("book the morning flight instead")

        assertFalse(decision.discard)
    }

    @Test
    fun returnsMissingContextWhenAgentTextIsNotSet() {
        val filter = SelfSpeechFilter()

        val decision = filter.shouldDiscard("hello there")

        assertFalse(decision.discard)
        assertEquals("missing-context", decision.reason)
    }

    @Test
    fun clearRemovesTheCurrentAgentText() {
        val filter = SelfSpeechFilter()
        filter.updateCurrentAgentText("Please repeat that back to me.")
        filter.clear()

        val decision = filter.shouldDiscard("repeat that back to me")

        assertFalse(decision.discard)
        assertEquals("missing-context", decision.reason)
    }

    @Test
    fun acceptsCustomInterruptCommandPrefixes() {
        val filter = SelfSpeechFilter(interruptCommands = setOf("hold up"))
        filter.updateCurrentAgentText("I can keep going if you want.")

        val decision = filter.shouldDiscard("hold up a second")

        assertFalse(decision.discard)
        assertEquals("protected-interrupt-command", decision.reason)
    }
}
