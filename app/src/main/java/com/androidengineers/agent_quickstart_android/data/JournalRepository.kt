package com.androidengineers.agent_quickstart_android.data

import com.androidengineers.agent_quickstart_android.data.local.JournalDao
import com.androidengineers.agent_quickstart_android.data.local.JournalEntryEntity
import com.androidengineers.agent_quickstart_android.data.local.JournalEntryTagCrossRef
import com.androidengineers.agent_quickstart_android.data.local.JournalEntryRow
import com.androidengineers.agent_quickstart_android.data.local.JournalTagEntity
import com.androidengineers.agent_quickstart_android.model.JournalEntryUiModel
import com.androidengineers.agent_quickstart_android.model.PracticeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JournalRepository(
    private val dao: JournalDao,
) {
    val entries: Flow<List<JournalEntryUiModel>> = dao.observeEntries()
        .map { entries -> entries.map { it.toUiModel() } }

    suspend fun saveCorrection(
        id: String,
        originalText: String,
        correctedText: String,
        tipText: String,
        practiceMode: PracticeMode,
        tags: List<String>,
        spokenAtMillis: Long,
    ) {
        val now = System.currentTimeMillis()
        val normalizedTags = tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf("Natural phrasing") }

        dao.upsertEntries(
            listOf(
                JournalEntryEntity(
                    id = id,
                    category = practiceMode.label,
                    originalText = originalText,
                    correctedText = correctedText,
                    tipText = tipText.ifBlank { null },
                    practiceMode = practiceMode.name,
                    spokenAtMillis = spokenAtMillis,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
        )
        dao.insertTags(normalizedTags.map(::JournalTagEntity))
        dao.upsertEntryTagRefs(
            normalizedTags.map { tag ->
                JournalEntryTagCrossRef(
                    entryId = id,
                    tagName = tag,
                )
            }
        )
    }
}

private fun JournalEntryRow.toUiModel(): JournalEntryUiModel {
    return JournalEntryUiModel(
        id = id,
        category = category,
        originalText = originalText,
        correctedText = correctedText,
        tags = tagsText
            ?.split("|")
            ?.filter { it.isNotBlank() }
            .orEmpty(),
        spokenAtMillis = spokenAtMillis,
    )
}
