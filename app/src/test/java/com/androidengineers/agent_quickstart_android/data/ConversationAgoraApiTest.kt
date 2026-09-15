package com.androidengineers.agent_quickstart_android.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConversationAgoraApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ConversationAgoraApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ConversationAgoraApi(
            baseUrl = server.url("/").toString(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun bootstrapUsesBackendContractWithoutClientAuthorization() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"app_id":"app","agent_rtc_uid":123456,"channel_name":"room-a","rtc_token":"rtc","rtm_token":"rtm","requester_rtc_uid":42,"requester_rtm_user_id":"42"}"""
            )
        )

        val result = api.requestSessionBootstrap()
        val request = server.takeRequest()

        assertEquals("app", result.appId)
        assertEquals(123456, result.agentRtcUid)
        assertEquals("room-a", result.channel)
        assertEquals("42", result.uid)
        assertEquals("/v1/conversation/bootstrap", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun joinUsesBackendContract() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"agent_id":"agent-123","created_at_unix":1714310400,"status":"started"}"""
            )
        )

        val result = api.inviteAgent("room-a", "42")
        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("agent-123", result.agentId)
        assertEquals("/v1/conversation/join", request.path)
        assertEquals("room-a", body.get("channel_name").asString)
        assertEquals(42, body.get("requester_rtc_uid").asInt)
    }

    @Test
    fun interruptLeaveAndRefreshUseBackendEndpoints() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"message":"interrupted"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"message":"left"}"""))
        server.enqueue(MockResponse().setBody("""{"rtc_token":"rtc-2","rtm_token":"rtm-2","expires_at_unix":1714314000}"""))

        api.interruptAgent("agent-1", "room-a")
        assertEquals("/v1/conversation/interrupt", server.takeRequest().path)
        api.stopConversation("agent-1", "room-a")
        assertEquals("/v1/conversation/leave", server.takeRequest().path)
        val tokens = api.renewTokens("room-a", 42, "42")
        assertEquals("/v1/conversation/refresh", server.takeRequest().path)
        assertEquals("rtc-2", tokens.rtcToken)
        assertEquals("rtm-2", tokens.rtmToken)
    }

    @Test
    fun textActionsUseDistinctSpeechAndInstructionContracts() = runBlocking {
        for (speak in listOf(false, true)) {
            for (append in listOf(false, true)) {
                server.enqueue(MockResponse().setBody("""{"success":true,"message":"accepted"}"""))
                api.sendText("agent-1", "room-a", "Hello", speak, append)
                val request = server.takeRequest()
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals("/v1/conversation/${if (speak) "speak" else "think"}", request.path)
                assertEquals("agent-1", body.get("agent_id").asString)
                assertEquals("room-a", body.get("channel_name").asString)
                assertEquals("Hello", body.get("text").asString)
                if (speak) {
                    assertEquals(if (append) "APPEND" else "INTERRUPT", body.get("priority").asString)
                    assertNull(body.get("on_speaking_action"))
                } else {
                    for (state in listOf("listening", "thinking", "speaking")) {
                        assertEquals(if (append) "append" else "interrupt", body.get("on_${state}_action").asString)
                    }
                    assertNull(body.get("priority"))
                }
            }
        }
    }
}
