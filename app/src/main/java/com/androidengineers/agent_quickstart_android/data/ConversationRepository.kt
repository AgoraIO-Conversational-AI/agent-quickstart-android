package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgentInviteResult
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.RenewalTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ConversationRepository(
    private val api: ConversationBackendApi = ConversationBackendApi(
        baseUrl = QuickstartConfig.backendBaseUrl,
    ),
) {
    suspend fun requestSessionBootstrap(): AgoraTokenBundle {
        return api.generateAgoraToken()
    }

    suspend fun inviteAgent(
        requesterId: String,
        channelName: String,
    ): AgentInviteResult {
        return api.inviteAgent(requesterId, channelName)
    }

    suspend fun stopConversation(agentId: String) {
        api.stopConversation(agentId)
    }

    suspend fun interruptConversation(agentId: String) {
        api.interruptAgent(agentId)
    }

    suspend fun renewTokens(
        channel: String,
        rtcUid: Int,
        rtmUserId: String,
    ): RenewalTokens = coroutineScope {
        val rtcToken = async {
            api.generateAgoraToken(
                channel = channel,
                uid = rtcUid.toString(),
                rtmUserId = rtmUserId,
            ).rtcToken
        }
        val rtmToken = async {
            api.generateAgoraToken(
                channel = channel,
                uid = rtcUid.toString(),
                rtmUserId = rtmUserId,
            ).rtmToken
        }
        RenewalTokens(
            rtcToken = rtcToken.await(),
            rtmToken = rtmToken.await(),
        )
    }
}
