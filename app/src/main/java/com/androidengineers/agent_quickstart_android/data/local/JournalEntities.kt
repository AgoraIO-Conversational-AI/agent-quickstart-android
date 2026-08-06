package com.androidengineers.agent_quickstart_android.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["spoken_at_millis"]),
        Index(value = ["category"]),
        Index(value = ["archived_at_millis"]),
    ],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val category: String,
    @ColumnInfo(name = "original_text") val originalText: String,
    @ColumnInfo(name = "corrected_text") val correctedText: String,
    @ColumnInfo(name = "tip_text") val tipText: String? = null,
    @ColumnInfo(name = "practice_mode") val practiceMode: String? = null,
    @ColumnInfo(name = "spoken_at_millis") val spokenAtMillis: Long,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
    @ColumnInfo(name = "archived_at_millis") val archivedAtMillis: Long? = null,
)

@Entity(
    tableName = "journal_tags",
)
data class JournalTagEntity(
    @PrimaryKey val name: String,
)

@Entity(
    tableName = "journal_entry_tag_cross_refs",
    primaryKeys = ["entry_id", "tag_name"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = JournalTagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tag_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["entry_id"]),
        Index(value = ["tag_name"]),
    ],
)
data class JournalEntryTagCrossRef(
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "tag_name") val tagName: String,
)
