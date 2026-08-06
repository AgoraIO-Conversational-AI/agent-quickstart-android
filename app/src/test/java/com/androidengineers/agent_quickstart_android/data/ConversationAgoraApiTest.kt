package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.PracticeMode
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

        val result = api.inviteAgent(
            channelName = "room-a",
            requesterRtcUid = "2468",
            practiceMode = PracticeMode.INTERVIEW,
        )
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        val properties = body.getJSONObject("properties")
        val systemPrompt = properties
            .getJSONObject("llm")
            .getJSONArray("system_messages")
            .getJSONObject(0)
            .getString("content")

        assertEquals("agent-123", result.agentId)
        assertEquals("/${QuickstartConfig.agoraAppId}/join", request.path)
        assertEquals("POST", request.method)
        assertTrue(requireNotNull(request.getHeader("Authorization")).startsWith("agora token="))
        assertTrue(body.getString("name").startsWith("android-rest-agent-"))
        assertEquals(expectedPreset(), body.getString("preset"))
        assertEquals("room-a", properties.getString("channel"))
        assertEquals(QuickstartConfig.agentUid.toString(), properties.getString("agent_rtc_uid"))
        assertEquals("2468", properties.getJSONArray("remote_rtc_uids").getString(0))
        assertFalse(properties.getBoolean("enable_string_uid"))
        assertEquals(30, properties.getInt("idle_timeout"))
        assertEquals(expectedGeofenceArea(QuickstartConfig.agoraArea), properties.getJSONObject("geofence").getString("area"))
        assertTrue(properties.getJSONObject("advanced_features").getBoolean("enable_rtm"))
        assertEquals(QuickstartConfig.normalizedAsrVendor(), properties.getJSONObject("asr").getString("vendor"))
        assertEquals(QuickstartConfig.asrLanguage, properties.getJSONObject("asr").getJSONObject("params").getString("language"))
        if (QuickstartConfig.isSarvamAsr) {
            assertEquals("en-US", properties.getJSONObject("asr").getString("language"))
            assertEquals(QuickstartConfig.sarvamApiKey, properties.getJSONObject("asr").getJSONObject("params").getString("api_key"))
        }
        assertEquals(15, properties.getJSONObject("llm").getInt("max_history"))
        assertTrue(systemPrompt.contains("Current practice mode: Interview"))
        assertTrue(systemPrompt.contains("job interviews"))
        assertTrue(systemPrompt.contains("IDENTITY"))
        assertTrue(systemPrompt.contains("OBJECTIVES"))
        assertTrue(systemPrompt.contains("LANGUAGE"))
        assertTrue(systemPrompt.contains("GUARDRAILS"))
        assertTrue(systemPrompt.contains("Mirror the user's register"))
        assertTrue(systemPrompt.contains("Never shame, mock, insult, or compare the learner."))
        assertTrue(systemPrompt.contains("Never claim a child or adult has a learning disability"))
        assertTrue(systemPrompt.contains("If a guardrail applies, the guardrail response wins over grammar correction."))
        assertTrue(systemPrompt.contains("CORRECTED must be the safe refusal or escalation response"))
        assertEquals(
            "Hi, I am BetterSaid, your friendly English practice coach. Speak in English, Hindi, or a natural mix. I can help you make your sentence clearer, but I will never judge you or label your ability. Speak freely, then tap Done Speaking.",
            properties.getJSONObject("llm").getString("greeting_message"),
        )
        assertEquals(
            "Please wait a moment while I shape that sentence.",
            properties.getJSONObject("llm").getString("failure_message"),
        )
        val ttsParams = properties.getJSONObject("tts").getJSONObject("params")
        assertEquals("murf", properties.getJSONObject("tts").getString("vendor"))
        assertEquals(QuickstartConfig.murfApiKey, ttsParams.getString("api_key"))
        assertEquals(QuickstartConfig.murfBaseUrl, ttsParams.getString("base_url"))
        assertEquals(QuickstartConfig.murfVoiceId, ttsParams.getString("voiceId"))
        assertEquals(QuickstartConfig.murfLocale, ttsParams.getString("locale"))
        assertEquals(QuickstartConfig.murfModel, ttsParams.getString("model"))
        assertEquals(24000, ttsParams.getInt("sample_rate"))
        assertEquals("default", properties.getJSONObject("turn_detection").getString("mode"))
        assertEquals("vad", properties.getJSONObject("turn_detection").getJSONObject("config").getJSONObject("start_of_speech").getString("mode"))
        assertEquals("vad", properties.getJSONObject("turn_detection").getJSONObject("config").getJSONObject("end_of_speech").getString("mode"))
        assertTrue(properties.getJSONObject("interruption").getBoolean("enable"))
        assertEquals("start_of_speech", properties.getJSONObject("interruption").getString("mode"))
        val parameters = properties.getJSONObject("parameters")
        assertEquals("chorus", parameters.getString("audio_scenario"))
        assertEquals("rtm", parameters.getString("data_channel"))
        assertTrue(parameters.getBoolean("enable_error_message"))
        assertTrue(parameters.getBoolean("enable_metrics"))
    }

    @Test
    fun inviteAgentRetriesWithANewNameOnConflict() = runBlocking {
        assumeTrue(QuickstartConfig.isConfigured)
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"reason":"agent name already exists"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "agent_id": "agent-456",
                      "create_ts": 1714310401,
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

        val result = api.inviteAgent(
            channelName = "room-a",
            requesterRtcUid = "2468",
            practiceMode = PracticeMode.DAILY_LIFE,
        )
        val firstRequest = JSONObject(server.takeRequest().body.readUtf8())
        val secondRequest = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("agent-456", result.agentId)
        assertFalse(firstRequest.getString("name") == secondRequest.getString("name"))
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
        assertTrue(requireNotNull(request.getHeader("Authorization")).startsWith("agora token="))
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
        assertTrue(requireNotNull(request.getHeader("Authorization")).startsWith("agora token="))
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

    private fun expectedPreset(): String {
        return if (QuickstartConfig.isSarvamAsr) {
            "openai_gpt_4o_mini"
        } else {
            "deepgram_nova_3,openai_gpt_4o_mini"
        }
    }
}
