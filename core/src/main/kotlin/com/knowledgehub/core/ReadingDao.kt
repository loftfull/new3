package com.knowledgehub.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ReadingDao {
    @Query("SELECT * FROM notes WHERE materialId = :materialId ORDER BY updatedAt DESC, id DESC")
    fun observeNotes(materialId: Long): Flow<List<MaterialNote>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findNote(id: Long): MaterialNote?

    @Insert
    suspend fun insertNote(note: MaterialNote): Long

    @Query(
        """UPDATE notes SET body = :body, revision = revision + 1, updatedAt = MAX(updatedAt, :time)
           WHERE id = :id AND revision = :expectedRevision"""
    )
    suspend fun editNote(id: Long, expectedRevision: Long, body: String, time: Long): Int

    @Insert
    suspend fun insertEvent(event: ReadingEvent)

    @Query(
        """SELECT * FROM reading_events
           WHERE :beforeTime IS NULL OR openedAt < :beforeTime
              OR (openedAt = :beforeTime AND id < :beforeId)
           ORDER BY openedAt DESC, id DESC LIMIT :limit"""
    )
    fun observeHistory(beforeTime: Long?, beforeId: Long?, limit: Int): Flow<List<ReadingEvent>>

    @Query("SELECT EXISTS(SELECT 1 FROM read_later WHERE materialId = :materialId)")
    fun observeReadLaterMembership(materialId: Long): Flow<Boolean>

    @Query(
        """SELECT materials.* FROM read_later JOIN materials ON materials.id = read_later.materialId
           ORDER BY read_later.addedAt DESC, read_later.materialId DESC"""
    )
    fun observeReadLater(): Flow<List<Material>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addReadLater(entry: ReadLaterEntry)

    @Query("DELETE FROM read_later WHERE materialId = :materialId")
    suspend fun removeReadLater(materialId: Long)
}
