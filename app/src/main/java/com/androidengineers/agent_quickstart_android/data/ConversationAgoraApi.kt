package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgentInviteResult
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.BackendHealthResult
import com.androidengineers.agent_quickstart_android.model.RenewalTokens
import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

class ConversationAgoraApi(
    baseUrl: String = QuickstartConfig.backendBaseUrl,
) {
    private val service: ConversationBackendService = Retrofit.Builder()
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
        .create(ConversationBackendService::class.java)

    suspend fun checkHealth(): BackendHealthResult {
        val body = service.health().requireBody()
        return BackendHealthResult(status = body.status, version = body.version)
    }

    suspend fun requireOnDeviceBackend() {
        val specification = service.openApi().requireBody()
        val paths = specification.getAsJsonObject("paths")
        val resultFields = specification.getAsJsonObject("components")?.getAsJsonObject("schemas")
            ?.getAsJsonObject("DocumentResultRequest")?.getAsJsonObject("properties")
        if (paths == null || !paths.has("/v1/document/chat/completions") || resultFields?.has("final") != true) {
            throw IOException("The Python backend is outdated. Restart it with ./server/run.sh before starting an on-device Gemma session.")
        }
    }

    suspend fun requestSessionBootstrap(): AgoraTokenBundle {
        val body = service.bootstrap(
            request = BootstrapRequest(),
        ).requireBody()
        return AgoraTokenBundle(
            appId = body.appId.requireValue("app_id"),
            agentRtcUid = body.agentRtcUid,
            rtcToken = body.rtcToken.requireValue("rtc_token"),
            rtmToken = body.rtmToken.requireValue("rtm_token"),
            uid = body.requesterRtcUid.toString(),
            channel = body.channelName.requireValue("channel_name"),
            rtmUserId = body.requesterRtmUserId.requireValue("requester_rtm_user_id"),
        )
    }

    suspend fun renewTokens(
        channel: String,
        rtcUid: Int,
        rtmUserId: String,
    ): RenewalTokens {
        val body = service.refresh(
            request = RefreshRequest(
                channelName = channel,
                requesterRtcUid = rtcUid,
                requesterRtmUserId = rtmUserId,
            ),
        ).requireBody()
        return RenewalTokens(
            rtcToken = body.rtcToken.requireValue("rtc_token"),
            rtmToken = body.rtmToken.requireValue("rtm_token"),
        )
    }

    suspend fun inviteAgent(
        channelName: String,
        requesterRtcUid: String,
        documentToken: String? = null,
    ): AgentInviteResult {
        val body = service.join(
            request = JoinRequest(
                channelName = channelName,
                documentToken = documentToken,
                requesterRtcUid = requesterRtcUid.toIntOrNull()
                    ?: throw IOException("The requester RTC UID must be numeric."),
            ),
        ).requireBody()
        return AgentInviteResult(
            agentId = body.agentId.requireValue("agent_id"),
            createTimestampSeconds = body.createdAtUnix,
            state = body.status,
        )
    }

    suspend fun stopConversation(agentId: String, channelName: String) {
        service.leave(
            request = AgentActionRequest(agentId = agentId, channelName = channelName),
        ).requireSuccess()
    }

    suspend fun interruptAgent(agentId: String, channelName: String) {
        service.interrupt(
            request = AgentActionRequest(agentId = agentId, channelName = channelName),
        ).requireSuccess()
    }

    suspend fun sendText(agentId: String, channelName: String, text: String, speak: Boolean, append: Boolean) {
        val action = if (append) "append" else "interrupt"
        val request = TextActionRequest(
            agentId = agentId, channelName = channelName, text = text,
            priority = if (speak) action.uppercase() else null,
            onListeningAction = if (speak) null else action,
            onThinkingAction = if (speak) null else action,
            onSpeakingAction = if (speak) null else action,
        )
        if (speak) service.speak(request).requireSuccess() else service.think(request).requireSuccess()
    }

    data class DocumentQuestion(@SerializedName("request_id") val requestId: String?, val question: String?, val history: String? = null, @SerializedName("previous_question") val previousQuestion: String? = null)
    data class DocumentPoll(val channel_name: String, val token: String)
    data class DocumentResult(val channel_name: String, val token: String, val request_id: String, val answer: String, val final: Boolean = true)

    suspend fun pollDocument(channel: String, token: String): DocumentQuestion =
        service.pollDocument(DocumentPoll(channel, token)).requireBody()

    suspend fun submitDocumentResult(channel: String, token: String, requestId: String, answer: String, final: Boolean = true) {
        service.documentResult(DocumentResult(channel, token, requestId, answer, final)).requireSuccess()
    }

    data class DocumentStatus(val channel_name: String, val token: String, val request_id: String)
    data class DocumentStatusResult(val active: Boolean)
    suspend fun documentRequestActive(channel: String, token: String, id: String): Boolean =
        service.documentStatus(DocumentStatus(channel, token, id)).requireBody().active

    private fun <T> Response<T>.requireBody(): T {
        if (!isSuccessful) throw toIOException()
        return body() ?: throw IOException("The quickstart server returned an empty response.")
    }

    private fun Response<*>.requireSuccess() {
        if (!isSuccessful) throw toIOException()
    }

    private fun Response<*>.toIOException(): IOException {
        val payload = errorBody()?.string().orEmpty()
        val detail = runCatching { JSONObject(payload).optString("detail") }.getOrNull().orEmpty()
        return IOException(
            detail.ifBlank { "Quickstart server request failed with status ${code()}." }
        )
    }

    private fun String?.requireValue(key: String): String {
        return this?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Missing '$key' in quickstart server response.")
    }

    private fun String.normalizeBaseUrl(): String {
        val configured = trim().ifBlank { "https://localhost" }
        return if (configured.endsWith('/')) configured else "$configured/"
    }

    private interface ConversationBackendService {
        @GET("openapi.json")
        suspend fun openApi(): Response<JsonObject>

        @POST("v1/document/request-status")
        suspend fun documentStatus(@Body request: DocumentStatus): Response<DocumentStatusResult>

        @POST("v1/document/poll")
        suspend fun pollDocument(@Body request: DocumentPoll): Response<DocumentQuestion>

        @POST("v1/document/result")
        suspend fun documentResult(@Body request: DocumentResult): Response<ActionResponse>

        @POST("v1/conversation/speak")
        suspend fun speak(@Body request: TextActionRequest): Response<ActionResponse>

        @POST("v1/conversation/think")
        suspend fun think(@Body request: TextActionRequest): Response<ActionResponse>

        @GET("health")
        suspend fun health(): Response<HealthResponse>

        @POST("v1/conversation/bootstrap")
        suspend fun bootstrap(
            @Body request: BootstrapRequest,
        ): Response<BootstrapResponse>

        @POST("v1/conversation/join")
        suspend fun join(
            @Body request: JoinRequest,
        ): Response<JoinResponse>

        @POST("v1/conversation/interrupt")
        suspend fun interrupt(
            @Body request: AgentActionRequest,
        ): Response<ActionResponse>

        @POST("v1/conversation/leave")
        suspend fun leave(
            @Body request: AgentActionRequest,
        ): Response<ActionResponse>

        @POST("v1/conversation/refresh")
        suspend fun refresh(
            @Body request: RefreshRequest,
        ): Response<RefreshResponse>
    }

    private class BootstrapRequest

    private data class TextActionRequest(
        @SerializedName("agent_id") val agentId: String,
        @SerializedName("channel_name") val channelName: String,
        val text: String,
        val priority: String?,
        @SerializedName("on_listening_action") val onListeningAction: String?,
        @SerializedName("on_thinking_action") val onThinkingAction: String?,
        @SerializedName("on_speaking_action") val onSpeakingAction: String?,
        val interruptable: Boolean = true,
    )

    private data class JoinRequest(
        @SerializedName("document_token") val documentToken: String? = null,
        @SerializedName("channel_name") val channelName: String,
        @SerializedName("requester_rtc_uid") val requesterRtcUid: Int,
    )

    private data class AgentActionRequest(
        @SerializedName("agent_id") val agentId: String,
        @SerializedName("channel_name") val channelName: String,
    )

    private data class RefreshRequest(
        @SerializedName("channel_name") val channelName: String,
        @SerializedName("requester_rtc_uid") val requesterRtcUid: Int,
        @SerializedName("requester_rtm_user_id") val requesterRtmUserId: String,
    )

    private data class HealthResponse(val status: String, val version: String)

    private data class BootstrapResponse(
        @SerializedName("app_id") val appId: String? = null,
        @SerializedName("agent_rtc_uid") val agentRtcUid: Int,
        @SerializedName("channel_name") val channelName: String? = null,
        @SerializedName("rtc_token") val rtcToken: String? = null,
        @SerializedName("rtm_token") val rtmToken: String? = null,
        @SerializedName("requester_rtc_uid") val requesterRtcUid: Int,
        @SerializedName("requester_rtm_user_id") val requesterRtmUserId: String? = null,
    )

    private data class JoinResponse(
        @SerializedName("agent_id") val agentId: String? = null,
        @SerializedName("created_at_unix") val createdAtUnix: Long? = null,
        val status: String? = null,
    )

    private data class RefreshResponse(
        @SerializedName("rtc_token") val rtcToken: String? = null,
        @SerializedName("rtm_token") val rtmToken: String? = null,
    )

    private data class ActionResponse(val success: Boolean, val message: String)

    private companion object {
        const val NETWORK_TIMEOUT_SECONDS = 15L
    }
}
