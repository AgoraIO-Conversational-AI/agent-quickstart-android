package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgentInviteResult
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.RenewalTokens
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

class ConversationAgoraApi(
    private val appId: String = QuickstartConfig.agoraAppId,
    private val tokenFactory: AgoraLocalTokenFactory = AgoraLocalTokenFactory(),
    baseUrl: String = QuickstartConfig.convoAiBaseUrl,
) {
    private val service: AgoraConversationService = Retrofit.Builder()
        .baseUrl(baseUrl.normalizeBaseUrl())
        .client(
            OkHttpClient.Builder()
                .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AgoraConversationService::class.java)

    fun requestSessionBootstrap(): AgoraTokenBundle {
        return tokenFactory.createBootstrap()
    }

    fun renewTokens(
        channel: String,
        rtcUid: Int,
        rtmUserId: String,
    ): RenewalTokens {
        return tokenFactory.renewUserTokens(
            channelName = channel,
            rtcUid = rtcUid,
            rtmUserId = rtmUserId,
        )
    }

    suspend fun inviteAgent(
        channelName: String,
    ): AgentInviteResult {
        val agentToken = tokenFactory.buildAgentRestToken(channelName)
        val body = service.inviteAgent(
            appId = appId,
            authorization = authorizationHeader(agentToken),
            request = JoinAgentRequest(
                name = generateAgentName(),
                preset = DEFAULT_PRESET,
                properties = JoinAgentProperties(
                    channel = channelName,
                    token = agentToken,
                    agentRtcUid = QuickstartConfig.agentUid.toString(),
                    // Subscribe to all remote users for demo robustness.
                    remoteRtcUids = listOf("*"),
                    enableStringUid = false,
                    idleTimeout = 30,
                    geofence = JoinGeofence(
                        area = mapGeofenceArea(QuickstartConfig.agoraArea),
                    ),
                    advancedFeatures = JoinAdvancedFeatures(
                        enableRtm = true,
                    ),
                    asr = JoinAsr(
                        vendor = "deepgram",
                        params = JoinAsrParams(language = "en"),
                    ),
                    llm = JoinLlm(
                        systemMessages = listOf(
                            JoinSystemMessage(
                                role = "system",
                                content = ADA_PROMPT,
                            )
                        ),
                        maxHistory = 15,
                        greetingMessage = DEFAULT_GREETING,
                        failureMessage = DEFAULT_FAILURE_MESSAGE,
                        params = JoinLlmParams(
                            maxTokens = 1024,
                            temperature = 0.7,
                            topP = 0.95,
                        ),
                    ),
                    tts = JoinTts(
                        vendor = "minimax",
                        params = JoinTtsParams(
                            voiceSetting = JoinVoiceSetting(
                                voiceId = "English_captivating_female1",
                            )
                        ),
                    ),
                    turnDetection = JoinTurnDetection(
                        mode = "default",
                        config = JoinTurnDetectionConfig(
                            speechThreshold = 0.38,
                            startOfSpeech = JoinStartOfSpeech(
                                mode = "vad",
                                vadConfig = JoinStartVadConfig(
                                    interruptDurationMs = 160,
                                    speakingInterruptDurationMs = 160,
                                    prefixPaddingMs = 480,
                                ),
                            ),
                            endOfSpeech = JoinEndOfSpeech(
                                mode = "vad",
                                vadConfig = JoinEndVadConfig(
                                    silenceDurationMs = 720,
                                ),
                            ),
                        ),
                    ),
                    interruption = JoinInterruption(
                        enable = true,
                        mode = "start_of_speech",
                    ),
                    parameters = JoinParameters(
                        dataChannel = "rtm",
                        enableErrorMessage = true,
                    ),
                ),
            )
        ).requireBody()

        return AgentInviteResult(
            agentId = body.agentId.requireValue("agent_id"),
            createTimestampSeconds = body.createTimestampSeconds,
            state = body.status?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun stopConversation(
        agentId: String,
        channelName: String,
    ) {
        service.stopConversation(
            appId = appId,
            agentId = agentId,
            authorization = authorizationHeader(tokenFactory.buildAgentRestToken(channelName)),
        ).requireSuccess()
    }

    suspend fun interruptAgent(
        agentId: String,
        channelName: String,
    ) {
        service.interruptAgent(
            appId = appId,
            agentId = agentId,
            authorization = authorizationHeader(tokenFactory.buildAgentRestToken(channelName)),
            request = EmptyRequest,
        ).requireSuccess()
    }

    private fun authorizationHeader(token: String): String {
        return "agora token=$token"
    }

    private fun generateAgentName(): String {
        return "android-rest-agent-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    private fun mapGeofenceArea(area: String): String {
        return when (area.trim().uppercase(Locale.ROOT)) {
            "EU", "EUROPE" -> "EUROPE"
            "AP", "ASIA" -> "ASIA"
            "INDIA" -> "INDIA"
            "JAPAN" -> "JAPAN"
            "GLOBAL" -> "GLOBAL"
            "US", "NORTH_AMERICA" -> "NORTH_AMERICA"
            else -> "NORTH_AMERICA"
        }
    }

    private fun <T> Response<T>.requireBody(): T {
        if (!isSuccessful) {
            throw toIOException()
        }
        return body() ?: throw IOException("Agora REST response body was empty.")
    }

    private fun Response<*>.requireSuccess() {
        if (!isSuccessful) {
            throw toIOException()
        }
    }

    private fun Response<*>.toIOException(): IOException {
        val payload = errorBody()?.string().orEmpty()
        val json = payload.takeIf { it.isNotBlank() }?.let(::JSONObject)
        val detail = json?.optString("detail").orEmpty()
        val reason = json?.optString("reason").orEmpty()
        val message = reason.ifBlank { "Agora REST request failed with status ${code()}." }
        val composed = buildString {
            append(message)
            if (detail.isNotBlank()) {
                append(" (")
                append(detail)
                append(')')
            }
        }
        return IOException(composed)
    }

    private fun String?.requireValue(key: String): String {
        val value = this?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IOException("Missing '$key' in Agora REST response.")
        }
        return value
    }

    private fun String.normalizeBaseUrl(): String {
        val trimmed = trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private interface AgoraConversationService {
        @POST("{appId}/join")
        suspend fun inviteAgent(
            @Path("appId") appId: String,
            @Header("Authorization") authorization: String,
            @Body request: JoinAgentRequest,
        ): Response<JoinAgentResponse>

        @POST("{appId}/agents/{agentId}/leave")
        suspend fun stopConversation(
            @Path("appId") appId: String,
            @Path("agentId") agentId: String,
            @Header("Authorization") authorization: String,
        ): Response<ResponseBody>

        @POST("{appId}/agents/{agentId}/interrupt")
        suspend fun interruptAgent(
            @Path("appId") appId: String,
            @Path("agentId") agentId: String,
            @Header("Authorization") authorization: String,
            @Body request: EmptyRequest,
        ): Response<ResponseBody>
    }

    private data class JoinAgentRequest(
        @SerializedName("name") val name: String,
        @SerializedName("preset") val preset: String,
        @SerializedName("properties") val properties: JoinAgentProperties,
    )

    private data class JoinAgentProperties(
        @SerializedName("channel") val channel: String,
        @SerializedName("token") val token: String,
        @SerializedName("agent_rtc_uid") val agentRtcUid: String,
        @SerializedName("remote_rtc_uids") val remoteRtcUids: List<String>,
        @SerializedName("enable_string_uid") val enableStringUid: Boolean,
        @SerializedName("idle_timeout") val idleTimeout: Int,
        @SerializedName("geofence") val geofence: JoinGeofence,
        @SerializedName("advanced_features") val advancedFeatures: JoinAdvancedFeatures,
        @SerializedName("asr") val asr: JoinAsr,
        @SerializedName("llm") val llm: JoinLlm,
        @SerializedName("tts") val tts: JoinTts,
        @SerializedName("turn_detection") val turnDetection: JoinTurnDetection,
        @SerializedName("interruption") val interruption: JoinInterruption,
        @SerializedName("parameters") val parameters: JoinParameters,
    )

    private data class JoinGeofence(
        @SerializedName("area") val area: String,
    )

    private data class JoinAdvancedFeatures(
        @SerializedName("enable_rtm") val enableRtm: Boolean,
    )

    private data class JoinAsr(
        @SerializedName("vendor") val vendor: String,
        @SerializedName("params") val params: JoinAsrParams,
    )

    private data class JoinAsrParams(
        @SerializedName("language") val language: String,
    )

    private data class JoinLlm(
        @SerializedName("system_messages") val systemMessages: List<JoinSystemMessage>,
        @SerializedName("max_history") val maxHistory: Int,
        @SerializedName("greeting_message") val greetingMessage: String,
        @SerializedName("failure_message") val failureMessage: String,
        @SerializedName("params") val params: JoinLlmParams,
    )

    private data class JoinSystemMessage(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: String,
    )

    private data class JoinLlmParams(
        @SerializedName("max_tokens") val maxTokens: Int,
        @SerializedName("temperature") val temperature: Double,
        @SerializedName("top_p") val topP: Double,
    )

    private data class JoinTts(
        @SerializedName("vendor") val vendor: String,
        @SerializedName("params") val params: JoinTtsParams,
    )

    private data class JoinTtsParams(
        @SerializedName("voice_setting") val voiceSetting: JoinVoiceSetting,
    )

    private data class JoinVoiceSetting(
        @SerializedName("voice_id") val voiceId: String,
    )

    private data class JoinTurnDetection(
        @SerializedName("mode") val mode: String,
        @SerializedName("config") val config: JoinTurnDetectionConfig,
    )

    private data class JoinTurnDetectionConfig(
        @SerializedName("speech_threshold") val speechThreshold: Double,
        @SerializedName("start_of_speech") val startOfSpeech: JoinStartOfSpeech,
        @SerializedName("end_of_speech") val endOfSpeech: JoinEndOfSpeech,
    )

    private data class JoinStartOfSpeech(
        @SerializedName("mode") val mode: String,
        @SerializedName("vad_config") val vadConfig: JoinStartVadConfig,
    )

    private data class JoinStartVadConfig(
        @SerializedName("interrupt_duration_ms") val interruptDurationMs: Int,
        @SerializedName("speaking_interrupt_duration_ms") val speakingInterruptDurationMs: Int,
        @SerializedName("prefix_padding_ms") val prefixPaddingMs: Int,
    )

    private data class JoinEndOfSpeech(
        @SerializedName("mode") val mode: String,
        @SerializedName("vad_config") val vadConfig: JoinEndVadConfig,
    )

    private data class JoinEndVadConfig(
        @SerializedName("silence_duration_ms") val silenceDurationMs: Int,
    )

    private data class JoinInterruption(
        @SerializedName("enable") val enable: Boolean,
        @SerializedName("mode") val mode: String,
    )

    private data class JoinParameters(
        @SerializedName("data_channel") val dataChannel: String,
        @SerializedName("enable_error_message") val enableErrorMessage: Boolean,
    )

    private data class JoinAgentResponse(
        @SerializedName("agent_id") val agentId: String? = null,
        @SerializedName("create_ts") val createTimestampSeconds: Long? = null,
        @SerializedName("status") val status: String? = null,
    )

    private object EmptyRequest

    private companion object {
        const val NETWORK_TIMEOUT_SECONDS = 15L
        const val DEFAULT_PRESET =
            "deepgram_nova_3,openai_gpt_4o_mini,minimax_speech_2_6_turbo"
        const val DEFAULT_GREETING =
            "Hi there!"
        const val DEFAULT_FAILURE_MESSAGE = "Please wait a moment."

        const val ADA_PROMPT = """
You are **Ada**, an agentic developer advocate from **Agora**. You help developers understand and build with Agora's Conversational AI platform.

# What Agora Actually Is
Agora is a real-time communications company. The product you represent is the **Agora Conversational AI Engine**. It lets developers add voice AI agents to any app by connecting ASR, LLM, and TTS into a real-time pipeline over Agora's SD-RTN.

# Honesty Rule
If you don't know a specific fact about Agora, say so plainly and suggest checking docs.agora.io. Never invent product names, feature names, or capabilities.

# Persona & Tone
- Friendly, technically credible, concise.
- Plain English. No marketing fluff.

# Core Behavior Guidelines
- Default to brief for voice conversations.
- Never list or enumerate in speech.
- Clarify before answering anything complex.
- Ask at most one question per turn.
- Guide, don't lecture.
"""
    }
}
