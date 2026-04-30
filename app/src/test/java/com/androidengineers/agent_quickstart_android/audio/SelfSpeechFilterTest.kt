package com.androidengineers.agent_quickstart_android.audio

import org.junit.Assert.assertFalse
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
}
