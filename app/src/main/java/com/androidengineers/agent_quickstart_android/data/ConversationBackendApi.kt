package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.model.AgentInviteResult
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

class ConversationBackendApi(
    private val baseUrl: String,
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

    suspend fun generateAgoraToken(
        channel: String? = null,
        uid: String? = null,
        rtmUserId: String? = null,
    ): AgoraTokenBundle {
        val body = service.generateAgoraToken(
            channel = channel,
            uid = uid,
            rtmUserId = rtmUserId,
        ).requireBody()

        return AgoraTokenBundle(
            rtcToken = body.rtcToken?.takeIf { it.isNotBlank() } ?: body.legacyToken.requireValue("token"),
            rtmToken = body.rtmToken.requireValue("rtm_token"),
            uid = body.uid?.ifBlank { DEFAULT_UID } ?: DEFAULT_UID,
            channel = body.channel.requireValue("channel"),
            rtmUserId = body.rtmUserId.requireValue("rtm_user_id"),
        )
    }

    suspend fun inviteAgent(
        requesterId: String,
        channelName: String,
    ): AgentInviteResult {
        val body = service.inviteAgent(
            InviteAgentRequest(
                requesterId = requesterId,
                channelName = channelName,
            )
        ).requireBody()

        return AgentInviteResult(
            agentId = body.agentId.requireValue("agent_id"),
            createTimestampSeconds = body.createTimestampSeconds?.toLongOrNull(),
            state = body.state?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun stopConversation(agentId: String) {
        service.stopConversation(AgentIdRequest(agentId)).requireSuccess()
    }

    suspend fun interruptAgent(agentId: String) {
        service.interruptAgent(AgentIdRequest(agentId)).requireSuccess()
    }

    private fun <T> Response<T>.requireBody(): T {
        if (!isSuccessful) {
            throw toIOException()
        }
        return body() ?: throw IOException("Backend response body was empty.")
    }

    private fun Response<*>.requireSuccess() {
        if (!isSuccessful) {
            throw toIOException()
        }
    }

    private fun Response<*>.toIOException(): IOException {
        val payload = errorBody()?.string().orEmpty()
        val json = payload.takeIf { it.isNotBlank() }?.let(::JSONObject)
        val detail = json?.optString("details").orEmpty()
        val message = json?.optString("error")
            .orEmpty()
            .ifBlank { json?.optString("message").orEmpty() }
            .ifBlank { "Backend request failed with status ${code()}." }
        val composed = if (detail.isBlank()) message else "$message ($detail)"
        return IOException(composed)
    }

    private fun String?.requireValue(key: String): String {
        val value = this?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IOException("Missing '$key' in backend response.")
        }
        return value
    }

    private fun String.normalizeBaseUrl(): String {
        val trimmed = trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private interface ConversationBackendService {
        @GET("api/generate-agora-token")
        suspend fun generateAgoraToken(
            @Query("channel") channel: String?,
            @Query("uid") uid: String?,
            @Query("rtm_user_id") rtmUserId: String?,
        ): Response<GenerateAgoraTokenResponse>

        @POST("api/invite-agent")
        suspend fun inviteAgent(
            @Body request: InviteAgentRequest,
        ): Response<InviteAgentResponse>

        @POST("api/stop-conversation")
        suspend fun stopConversation(
            @Body request: AgentIdRequest,
        ): Response<ResponseBody>

        @POST("api/interrupt-agent")
        suspend fun interruptAgent(
            @Body request: AgentIdRequest,
        ): Response<ResponseBody>
    }

    private data class GenerateAgoraTokenResponse(
        @SerializedName("rtc_token") val rtcToken: String? = null,
        @SerializedName("token") val legacyToken: String? = null,
        @SerializedName("rtm_token") val rtmToken: String? = null,
        @SerializedName("uid") val uid: String? = null,
        @SerializedName("channel") val channel: String? = null,
        @SerializedName("rtm_user_id") val rtmUserId: String? = null,
    )

    private data class InviteAgentRequest(
        @SerializedName("requester_id") val requesterId: String,
        @SerializedName("channel_name") val channelName: String,
    )

    private data class InviteAgentResponse(
        @SerializedName("agent_id") val agentId: String? = null,
        @SerializedName("create_ts") val createTimestampSeconds: String? = null,
        @SerializedName("state") val state: String? = null,
    )

    private data class AgentIdRequest(
        @SerializedName("agent_id") val agentId: String,
    )

    private companion object {
        const val DEFAULT_UID = "0"
        const val NETWORK_TIMEOUT_SECONDS = 15L
    }
}
