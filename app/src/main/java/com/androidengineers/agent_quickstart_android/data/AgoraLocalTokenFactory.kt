package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.config.QuickstartConfig
import com.androidengineers.agent_quickstart_android.model.AgoraTokenBundle
import com.androidengineers.agent_quickstart_android.model.RenewalTokens
import io.agora.media.AccessToken2
import java.io.IOException
import kotlin.random.Random

class AgoraLocalTokenFactory(
    private val appId: String = QuickstartConfig.agoraAppId,
    private val appCertificate: String = QuickstartConfig.agoraAppCertificate,
    private val agentUid: Int = QuickstartConfig.agentUid,
) {
    fun createBootstrap(
        channelName: String? = null,
        rtcUid: Int? = null,
        rtmUserId: String? = null,
    ): AgoraTokenBundle {
        val resolvedUid = rtcUid ?: generateNumericUid()
        val resolvedChannel = channelName?.takeIf { it.isNotBlank() } ?: generateChannelName()
        val resolvedRtmUserId = rtmUserId?.takeIf { it.isNotBlank() } ?: "android-$resolvedUid"

        return AgoraTokenBundle(
            rtcToken = buildRtcClientToken(
                channelName = resolvedChannel,
                uid = resolvedUid,
            ),
            rtmToken = buildRtmClientToken(resolvedRtmUserId),
            uid = resolvedUid.toString(),
            channel = resolvedChannel,
            rtmUserId = resolvedRtmUserId,
        )
    }

    fun renewUserTokens(
        channelName: String,
        rtcUid: Int,
        rtmUserId: String,
    ): RenewalTokens {
        return RenewalTokens(
            rtcToken = buildRtcClientToken(channelName = channelName, uid = rtcUid),
            rtmToken = buildRtmClientToken(rtmUserId),
        )
    }

    fun buildAgentRestToken(channelName: String): String {
        val token = AccessToken2(appId, appCertificate, TOKEN_EXPIRY_SECONDS)
        val account = agentUid.toString()

        val rtcService = AccessToken2.ServiceRtc(channelName, account)
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_JOIN_CHANNEL,
            TOKEN_EXPIRY_SECONDS,
        )
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_AUDIO_STREAM,
            TOKEN_EXPIRY_SECONDS,
        )
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_VIDEO_STREAM,
            TOKEN_EXPIRY_SECONDS,
        )
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_DATA_STREAM,
            TOKEN_EXPIRY_SECONDS,
        )
        token.addService(rtcService)

        val rtmService = AccessToken2.ServiceRtm(account)
        rtmService.addPrivilegeRtm(
            AccessToken2.PrivilegeRtm.PRIVILEGE_LOGIN,
            TOKEN_EXPIRY_SECONDS,
        )
        token.addService(rtmService)

        return token.buildOrThrow("combined RTC + RTM agent token")
    }

    private fun buildRtcClientToken(
        channelName: String,
        uid: Int,
    ): String {
        val token = AccessToken2(appId, appCertificate, TOKEN_EXPIRY_SECONDS)
        val rtcService = AccessToken2.ServiceRtc(channelName, AccessToken2.getUidStr(uid))
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_JOIN_CHANNEL,
            TOKEN_EXPIRY_SECONDS,
        )
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_AUDIO_STREAM,
            TOKEN_EXPIRY_SECONDS,
        )
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_VIDEO_STREAM,
            TOKEN_EXPIRY_SECONDS,
        )
        rtcService.addPrivilegeRtc(
            AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_DATA_STREAM,
            TOKEN_EXPIRY_SECONDS,
        )
        token.addService(rtcService)
        return token.buildOrThrow("RTC client token")
    }

    private fun buildRtmClientToken(
        userId: String,
    ): String {
        val token = AccessToken2(appId, appCertificate, TOKEN_EXPIRY_SECONDS)
        val rtmService = AccessToken2.ServiceRtm(userId)
        rtmService.addPrivilegeRtm(
            AccessToken2.PrivilegeRtm.PRIVILEGE_LOGIN,
            TOKEN_EXPIRY_SECONDS,
        )
        token.addService(rtmService)
        return token.buildOrThrow("RTM client token")
    }

    private fun AccessToken2.buildOrThrow(label: String): String {
        return try {
            build().takeIf { it.isNotBlank() }
                ?: throw IOException(
                    "Failed to generate $label. Verify AGORA_APP_ID and AGORA_APP_CERTIFICATE."
                )
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Failed to generate $label.", error)
        }
    }

    private fun generateChannelName(): String {
        val timestamp = System.currentTimeMillis()
        val random = Random.nextInt(100_000, 999_999)
        return "android-convoai-$timestamp-$random"
    }

    private fun generateNumericUid(): Int {
        return Random.nextInt(100_000, 900_000)
    }

    companion object {
        private const val TOKEN_EXPIRY_SECONDS = 3600
    }
}
