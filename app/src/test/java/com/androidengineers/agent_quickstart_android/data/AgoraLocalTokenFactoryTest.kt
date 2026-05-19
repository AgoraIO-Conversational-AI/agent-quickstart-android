package com.androidengineers.agent_quickstart_android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgoraLocalTokenFactoryTest {
    @Test
    fun createBootstrapHonorsExplicitOverrides() {
        assumeTrue(com.androidengineers.agent_quickstart_android.config.QuickstartConfig.isConfigured)
        val factory = AgoraLocalTokenFactory(
            appId = com.androidengineers.agent_quickstart_android.config.QuickstartConfig.agoraAppId,
            appCertificate = com.androidengineers.agent_quickstart_android.config.QuickstartConfig.agoraAppCertificate,
            agentUid = 2468,
        )

        val bootstrap = factory.createBootstrap(
            channelName = "room-a",
            rtcUid = 42,
            rtmUserId = "user-a",
        )

        assertEquals("42", bootstrap.uid)
        assertEquals("room-a", bootstrap.channel)
        assertEquals("user-a", bootstrap.rtmUserId)
        assertFalse(bootstrap.rtcToken.isBlank())
        assertFalse(bootstrap.rtmToken.isBlank())
    }

    @Test
    fun renewUserTokensBuildsBothRtcAndRtmTokens() {
        assumeTrue(com.androidengineers.agent_quickstart_android.config.QuickstartConfig.isConfigured)
        val factory = AgoraLocalTokenFactory(
            appId = com.androidengineers.agent_quickstart_android.config.QuickstartConfig.agoraAppId,
            appCertificate = com.androidengineers.agent_quickstart_android.config.QuickstartConfig.agoraAppCertificate,
            agentUid = 2468,
        )

        val renewal = factory.renewUserTokens(
            channelName = "room-b",
            rtcUid = 99,
            rtmUserId = "user-b",
        )

        assertFalse(renewal.rtcToken.isBlank())
        assertFalse(renewal.rtmToken.isBlank())
    }

    @Test
    fun buildAgentRestTokenReturnsATokenString() {
        assumeTrue(com.androidengineers.agent_quickstart_android.config.QuickstartConfig.isConfigured)
        val factory = AgoraLocalTokenFactory(
            appId = com.androidengineers.agent_quickstart_android.config.QuickstartConfig.agoraAppId,
            appCertificate = com.androidengineers.agent_quickstart_android.config.QuickstartConfig.agoraAppCertificate,
            agentUid = 1357,
        )

        val token = factory.buildAgentRestToken("room-c")

        assertNotNull(token)
        assertTrue(token.isNotBlank())
    }
}
