package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.model.AgentInviteResult
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.RenewalTokens

class ConversationRepository(
    private val api: ConversationAgoraApi = ConversationAgoraApi(),
) {
    fun requestSessionBootstrap(): AgoraTokenBundle {
        return api.requestSessionBootstrap()
    }

    suspend fun inviteAgent(
        requesterId: String,
        channelName: String,
    ): AgentInviteResult {
        return api.inviteAgent(requesterId, channelName)
    }

    suspend fun stopConversation(
        agentId: String,
        channelName: String,
    ) {
        api.stopConversation(agentId, channelName)
    }

    suspend fun interruptConversation(
        agentId: String,
        channelName: String,
    ) {
        api.interruptAgent(agentId, channelName)
    }

    suspend fun renewTokens(
        channel: String,
        rtcUid: Int,
        rtmUserId: String,
    ): RenewalTokens {
        return api.renewTokens(
            channel = channel,
            rtcUid = rtcUid,
            rtmUserId = rtmUserId,
        )
    }
}
