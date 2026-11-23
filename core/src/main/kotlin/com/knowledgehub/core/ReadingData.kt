package com.knowledgehub.core

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = Material::class,
        parentColumns = ["id"],
        childColumns = ["materialId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("materialId")],
)
data class MaterialNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val body: String,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Fts4(contentEntity = MaterialNote::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "notes_fts")
internal data class NoteIndex(val body: String)

sealed interface NoteEditResult {
    data class Saved(val note: MaterialNote) : NoteEditResult
    data class Conflict(val current: MaterialNote) : NoteEditResult
    data object Missing : NoteEditResult
}

@Entity(
    tableName = "reading_events",
    foreignKeys = [ForeignKey(
        entity = Material::class,
        parentColumns = ["id"],
        childColumns = ["materialId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("materialId"), Index(value = ["openedAt", "id"])],
)
data class ReadingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val openedAt: Long,
)

@Entity(
    tableName = "read_later",
    foreignKeys = [ForeignKey(
        entity = Material::class,
        parentColumns = ["id"],
        childColumns = ["materialId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class ReadLaterEntry(@PrimaryKey val materialId: Long, val addedAt: Long)
