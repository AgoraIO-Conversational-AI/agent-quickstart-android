package com.androidengineers.agent_quickstart_android.config

import com.androidengineers.agent_quickstart_android.BuildConfig

object QuickstartConfig {
    val agoraAppId: String = BuildConfig.AGORA_APP_ID.trim()
    val agoraAppCertificate: String = BuildConfig.AGORA_APP_CERTIFICATE.trim()
    val convoAiBaseUrl: String = BuildConfig.AGORA_CONVOAI_BASE_URL.trim().trimEnd('/')
    val agoraArea: String = BuildConfig.AGORA_AREA.trim()
    val agentUid: Int = BuildConfig.AGENT_UID
    val murfApiKey: String = BuildConfig.MURF_API_KEY.trim()
    val murfBaseUrl: String = BuildConfig.MURF_BASE_URL.trim()
    val murfVoiceId: String = BuildConfig.MURF_VOICE_ID.trim()
    val murfLocale: String = BuildConfig.MURF_LOCALE.trim()
    val murfModel: String = BuildConfig.MURF_MODEL.trim()

    fun missingRequiredValues(): List<String> {
        val missing = mutableListOf<String>()
        if (agoraAppId.isBlank()) {
            missing += "AGORA_APP_ID"
        }
        if (agoraAppCertificate.isBlank()) {
            missing += "AGORA_APP_CERTIFICATE"
        }
        if (murfApiKey.isBlank()) {
            missing += "MURF_API_KEY"
        }
        return missing
    }

    val isConfigured: Boolean
        get() = missingRequiredValues().isEmpty()

    fun startupHelpMessage(): String? {
        val missing = missingRequiredValues()
        if (missing.isEmpty()) {
            return null
        }
        return "Add ${missing.joinToString()} to local.properties before starting the Android quickstart."
    }
}
