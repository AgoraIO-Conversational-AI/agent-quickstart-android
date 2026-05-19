package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationAgoraApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun inviteAgentPostsExpectedPayloadAndAuthorizationHeader() = runBlocking {
        assumeTrue(QuickstartConfig.isConfigured)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "agent_id": "agent-123",
                      "create_ts": 1714310400,
                      "status": "STARTING"
                    }
                    """.trimIndent()
                )
        )

        val api = ConversationAgoraApi(
            appId = QuickstartConfig.agoraAppId,
            tokenFactory = AgoraLocalTokenFactory(
                appId = QuickstartConfig.agoraAppId,
                appCertificate = QuickstartConfig.agoraAppCertificate,
                agentUid = 1357,
            ),
            baseUrl = server.url("/").toString(),
        )

        val result = api.inviteAgent("room-a")
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        val properties = body.getJSONObject("properties")

        assertEquals("agent-123", result.agentId)
        assertEquals("/${QuickstartConfig.agoraAppId}/join", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")!!.startsWith("agora token="))
        assertTrue(body.getString("name").startsWith("android-rest-agent-"))
        assertEquals("deepgram_nova_3,openai_gpt_4o_mini,minimax_speech_2_6_turbo", body.getString("preset"))
        assertEquals("room-a", properties.getString("channel"))
        assertEquals(QuickstartConfig.agentUid.toString(), properties.getString("agent_rtc_uid"))
        assertEquals("*", properties.getJSONArray("remote_rtc_uids").getString(0))
        assertFalse(properties.getBoolean("enable_string_uid"))
        assertEquals(30, properties.getInt("idle_timeout"))
        assertEquals(expectedGeofenceArea(QuickstartConfig.agoraArea), properties.getJSONObject("geofence").getString("area"))
        assertTrue(properties.getJSONObject("advanced_features").getBoolean("enable_rtm"))
        assertEquals("deepgram", properties.getJSONObject("asr").getString("vendor"))
        assertEquals("en", properties.getJSONObject("asr").getJSONObject("params").getString("language"))
        assertEquals(15, properties.getJSONObject("llm").getInt("max_history"))
        assertEquals("Hi there!", properties.getJSONObject("llm").getString("greeting_message"))
        assertEquals("Please wait a moment.", properties.getJSONObject("llm").getString("failure_message"))
        assertEquals("minimax", properties.getJSONObject("tts").getString("vendor"))
        assertEquals("English_captivating_female1", properties.getJSONObject("tts").getJSONObject("params").getJSONObject("voice_setting").getString("voice_id"))
        assertEquals("default", properties.getJSONObject("turn_detection").getString("mode"))
        assertEquals("vad", properties.getJSONObject("turn_detection").getJSONObject("config").getJSONObject("start_of_speech").getString("mode"))
        assertEquals("vad", properties.getJSONObject("turn_detection").getJSONObject("config").getJSONObject("end_of_speech").getString("mode"))
        assertTrue(properties.getJSONObject("interruption").getBoolean("enable"))
        assertEquals("start_of_speech", properties.getJSONObject("interruption").getString("mode"))
        assertEquals("rtm", properties.getJSONObject("parameters").getString("data_channel"))
        assertTrue(properties.getJSONObject("parameters").getBoolean("enable_error_message"))
    }

    @Test
    fun stopConversationUsesLeaveEndpointAndAuthorizationHeader() = runBlocking {
        assumeTrue(QuickstartConfig.isConfigured)
        server.enqueue(MockResponse().setResponseCode(200))

        val api = ConversationAgoraApi(
            appId = QuickstartConfig.agoraAppId,
            tokenFactory = AgoraLocalTokenFactory(
                appId = QuickstartConfig.agoraAppId,
                appCertificate = QuickstartConfig.agoraAppCertificate,
                agentUid = 1357,
            ),
            baseUrl = server.url("/").toString(),
        )

        api.stopConversation("agent-9", "room-z")
        val request = server.takeRequest()

        assertEquals("/${QuickstartConfig.agoraAppId}/agents/agent-9/leave", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")!!.startsWith("agora token="))
        assertEquals("", request.body.readUtf8())
    }

    @Test
    fun interruptAgentUsesInterruptEndpointAndEmptyJsonBody() = runBlocking {
        assumeTrue(QuickstartConfig.isConfigured)
        server.enqueue(MockResponse().setResponseCode(200))

        val api = ConversationAgoraApi(
            appId = QuickstartConfig.agoraAppId,
            tokenFactory = AgoraLocalTokenFactory(
                appId = QuickstartConfig.agoraAppId,
                appCertificate = QuickstartConfig.agoraAppCertificate,
                agentUid = 1357,
            ),
            baseUrl = server.url("/").toString(),
        )

        api.interruptAgent("agent-7", "room-q")
        val request = server.takeRequest()

        assertEquals("/${QuickstartConfig.agoraAppId}/agents/agent-7/interrupt", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")!!.startsWith("agora token="))
        assertEquals("{}", request.body.readUtf8())
    }

    private fun expectedGeofenceArea(area: String): String {
        return when (area.trim().uppercase()) {
            "EU", "EUROPE" -> "EUROPE"
            "AP", "ASIA" -> "ASIA"
            "INDIA" -> "INDIA"
            "JAPAN" -> "JAPAN"
            "GLOBAL" -> "GLOBAL"
            "US", "NORTH_AMERICA" -> "NORTH_AMERICA"
            else -> "NORTH_AMERICA"
        }
    }
}
