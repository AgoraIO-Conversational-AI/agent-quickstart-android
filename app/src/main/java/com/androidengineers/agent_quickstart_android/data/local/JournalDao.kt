package com.androidengineers.agent_quickstart_android.data.local

import androidx.room3.Dao
import androidx.room3.ColumnInfo
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

data class JournalEntryRow(
    val id: String,
    val category: String,
    @ColumnInfo(name = "original_text") val originalText: String,
    @ColumnInfo(name = "corrected_text") val correctedText: String,
    @ColumnInfo(name = "spoken_at_millis") val spokenAtMillis: Long,
    @ColumnInfo(name = "tags_text") val tagsText: String?,
)

@Dao
interface JournalDao {
    @Transaction
    @Query(
        """
        SELECT
            e.id,
            e.category,
            e.original_text,
            e.corrected_text,
            e.spoken_at_millis,
            GROUP_CONCAT(t.name, '|') AS tags_text
        FROM journal_entries AS e
        LEFT JOIN journal_entry_tag_cross_refs AS r ON r.entry_id = e.id
        LEFT JOIN journal_tags AS t ON t.name = r.tag_name
        WHERE e.archived_at_millis IS NULL
        GROUP BY e.id
        ORDER BY e.spoken_at_millis DESC
        """,
    )
    fun observeEntries(): Flow<List<JournalEntryRow>>

    @Query(
        """
        SELECT COUNT(*) FROM journal_entries
        WHERE archived_at_millis IS NULL
        """,
    )
    suspend fun activeEntryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<JournalEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<JournalTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntryTagRefs(refs: List<JournalEntryTagCrossRef>)
}
