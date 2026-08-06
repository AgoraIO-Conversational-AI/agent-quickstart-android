package com.androidengineers.agent_quickstart_android.model

data class JournalEntryUiModel(
    val id: String,
    val category: String,
    val originalText: String,
    val correctedText: String,
    val tags: List<String>,
    val spokenAtMillis: Long,
)
