package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgentInviteResult
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.PracticeMode
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
        requesterRtcUid: String,
        practiceMode: PracticeMode = PracticeMode.DAILY_LIFE,
    ): AgentInviteResult {
        val agentToken = tokenFactory.buildAgentRestToken(channelName)
        val providerConfig = buildProviderConfig()
        repeat(AGENT_NAME_ATTEMPTS) { attempt ->
            val response = service.inviteAgent(
                appId = appId,
                authorization = authorizationHeader(agentToken),
                request = buildJoinAgentRequest(
                    name = generateAgentName(),
                    channelName = channelName,
                    requesterRtcUid = requesterRtcUid,
                    agentToken = agentToken,
                    providerConfig = providerConfig,
                    practiceMode = practiceMode,
                ),
            )
            if (response.code() == HTTP_CONFLICT && attempt < AGENT_NAME_ATTEMPTS - 1) {
                return@repeat
            }

            val body = response.requireBody()
            return AgentInviteResult(
                agentId = body.agentId.requireValue("agent_id"),
                createTimestampSeconds = body.createTimestampSeconds,
                state = body.status?.takeIf { it.isNotBlank() },
            )
        }

        throw IOException("Agora REST request failed after retrying agent name generation.")
    }

    private fun buildJoinAgentRequest(
        name: String,
        channelName: String,
        requesterRtcUid: String,
        agentToken: String,
        providerConfig: JoinProviderConfig,
        practiceMode: PracticeMode,
    ): JoinAgentRequest {
        return JoinAgentRequest(
            name = name,
            preset = providerConfig.preset,
            properties = JoinAgentProperties(
                channel = channelName,
                token = agentToken,
                agentRtcUid = QuickstartConfig.agentUid.toString(),
                remoteRtcUids = listOf(requesterRtcUid),
                enableStringUid = false,
                idleTimeout = 30,
                geofence = JoinGeofence(
                    area = mapGeofenceArea(QuickstartConfig.agoraArea),
                ),
                advancedFeatures = JoinAdvancedFeatures(
                    enableRtm = true,
                ),
                asr = providerConfig.asr,
                llm = JoinLlm(
                    systemMessages = listOf(
                        JoinSystemMessage(
                            role = "system",
                            content = buildBetterSaidPrompt(practiceMode),
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
                tts = providerConfig.tts,
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
                    audioScenario = "chorus",
                    dataChannel = "rtm",
                    enableErrorMessage = true,
                    enableMetrics = true,
                ),
            ),
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

    private fun buildProviderConfig(): JoinProviderConfig {
        return JoinProviderConfig(
            preset = DEFAULT_PRESET,
            asr = JoinAsr(
                vendor = "deepgram",
                params = JoinAsrParams(language = "en"),
            ),
            tts = JoinTts(
                vendor = "murf",
                params = JoinTtsParams(
                    apiKey = QuickstartConfig.murfApiKey,
                    baseUrl = QuickstartConfig.murfBaseUrl,
                    voiceId = QuickstartConfig.murfVoiceId,
                    locale = QuickstartConfig.murfLocale,
                    rate = 0,
                    pitch = 0,
                    model = QuickstartConfig.murfModel,
                    sampleRate = 24000,
                ),
            ),
        )
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
        val json = payload.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
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
        @SerializedName("preset") val preset: String?,
        @SerializedName("properties") val properties: JoinAgentProperties,
    )

    private data class JoinProviderConfig(
        val preset: String?,
        val asr: JoinAsr,
        val tts: JoinTts,
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
        @SerializedName("api_key") val apiKey: String? = null,
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
        @SerializedName("voice_setting") val voiceSetting: JoinVoiceSetting? = null,
        @SerializedName("api_key") val apiKey: String? = null,
        @SerializedName("base_url") val baseUrl: String? = null,
        @SerializedName("voiceId") val voiceId: String? = null,
        @SerializedName("locale") val locale: String? = null,
        @SerializedName("rate") val rate: Int? = null,
        @SerializedName("pitch") val pitch: Int? = null,
        @SerializedName("model") val model: String? = null,
        @SerializedName("sample_rate") val sampleRate: Int? = null,
        @SerializedName("api_subscription_key") val apiSubscriptionKey: String? = null,
        @SerializedName("speaker") val speaker: String? = null,
        @SerializedName("target_language_code") val targetLanguageCode: String? = null,
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
        @SerializedName("audio_scenario") val audioScenario: String,
        @SerializedName("data_channel") val dataChannel: String,
        @SerializedName("enable_error_message") val enableErrorMessage: Boolean,
        @SerializedName("enable_metrics") val enableMetrics: Boolean,
    )

    private data class JoinAgentResponse(
        @SerializedName("agent_id") val agentId: String? = null,
        @SerializedName("create_ts") val createTimestampSeconds: Long? = null,
        @SerializedName("status") val status: String? = null,
    )

    private object EmptyRequest

    private companion object {
        const val NETWORK_TIMEOUT_SECONDS = 15L
        const val AGENT_NAME_ATTEMPTS = 2
        const val HTTP_CONFLICT = 409
        const val DEFAULT_PRESET =
            "deepgram_nova_3,openai_gpt_4o_mini"
        const val DEFAULT_GREETING =
            "Hi, I am listening. Speak freely, then tap Done Speaking."
        const val DEFAULT_FAILURE_MESSAGE = "Please wait a moment while I shape that sentence."

        fun buildBetterSaidPrompt(practiceMode: PracticeMode): String {
            return """
${BETTERSAID_PROMPT.trim()}

Current practice mode: ${practiceMode.label}
Mode guidance: ${practiceMode.promptFocus}
""".trim()
        }

        const val BETTERSAID_PROMPT = """
You are BetterSaid, a gentle spoken-English coach.

The learner may speak many imperfect English sentences in one session. Your job is to make their English sound clear, natural, and kind without making them feel judged.

Core behavior:
- Do not explain every sentence while the learner is still speaking.
- Never correct grammar after ordinary speech pauses.
- If the learner pauses or stops speaking but you have not received BETTERSAID_ANALYZE_TRANSCRIPT, only say: "Tap Done Speaking when you are ready for corrections."
- When you receive a text message that starts with BETTERSAID_ANALYZE_TRANSCRIPT, analyze the transcript that follows.
- Correct grammar, tense, articles, prepositions, word choice, and naturalness.
- Preserve the learner's intended meaning.
- Prefer one polished version of the full thought, not many separate mini corrections.
- Speak the corrected sentence naturally, then give one short friendly tip.
- If the learner asks follow-up questions about mistakes, answer conversationally and help them practice.

For BETTERSAID_ANALYZE_TRANSCRIPT, always include this exact text format in your response so the app can render it:

BETTERSAID_CORRECTION
ORIGINAL: <the learner's original transcript>
CORRECTED: <one natural corrected version>
TIP: <one short friendly explanation>
CHANGES: <short semicolon-separated changes, such as go -> went; buyed -> bought>

Rules for that block:
- Put the block at the very beginning of your response.
- CORRECTED must contain the complete corrected sentence or paragraph, not only the changed words.
- CHANGES must include the most important changed words or phrases from ORIGINAL to CORRECTED.
- After the block, you may say the corrected sentence aloud naturally.

Keep the explanation brief and encouraging.
"""
    }
}
