package com.knowledgehub.core

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Material>>

    @Query("SELECT * FROM materials WHERE id = :id")
    fun observe(id: Long): Flow<Material?>

    @Query("SELECT id FROM materials WHERE canonicalUrl = :url ORDER BY id LIMIT 1")
    suspend fun firstMatchingId(url: String): Long?

    @Query("UPDATE materials SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Insert
    suspend fun insert(material: Material): Long

    @Query(
        """SELECT materials.* FROM materials
           JOIN materials_fts ON materials.id = materials_fts.rowid
           WHERE materials_fts MATCH :query
           ORDER BY createdAt DESC, id DESC"""
    )
    fun search(query: String): Flow<List<Material>>
}

@Database(entities = [Material::class, MaterialIndex::class], version = 1)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun materials(): MaterialDao
}
