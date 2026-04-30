package com.androidengineers.agent_quickstart_android.config

import com.androidengineers.agent_quickstart_android.BuildConfig

object QuickstartConfig {
    val agoraAppId: String = BuildConfig.AGORA_APP_ID.trim()
    val backendBaseUrl: String = BuildConfig.AGORA_BACKEND_BASE_URL.trim().trimEnd('/')
    val agentUid: Int = BuildConfig.AGENT_UID

    fun missingRequiredValues(): List<String> {
        val missing = mutableListOf<String>()
        if (agoraAppId.isBlank()) {
            missing += "AGORA_APP_ID"
        }
        if (backendBaseUrl.isBlank()) {
            missing += "AGORA_BACKEND_BASE_URL"
        }
        return missing
    }

    val isConfigured: Boolean
        get() = missingRequiredValues().isEmpty()
}
