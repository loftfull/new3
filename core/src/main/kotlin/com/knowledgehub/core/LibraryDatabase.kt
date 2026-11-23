package com.knowledgehub.core

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import com.knowledgehub.core.processing.ProcessingDao
import com.knowledgehub.core.processing.ProcessingRecord
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Material>>

    @Query("SELECT * FROM materials ORDER BY id")
    suspend fun exportMaterials(): List<Material>

    @Query("SELECT * FROM materials WHERE id = :id")
    fun observe(id: Long): Flow<Material?>

    @Query("SELECT id FROM materials WHERE canonicalUrl = :url ORDER BY id LIMIT 1")
    suspend fun firstMatchingId(url: String): Long?

    @Query("UPDATE materials SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Insert
    suspend fun insert(material: Material): Long

    @Query("""
        WITH signals AS (
            SELECT candidate.id,
                CASE WHEN original.source IS NOT NULL AND candidate.source = original.source THEN 2 ELSE 0 END +
                CASE WHEN originalAnalysis.suggestedTopic = candidateAnalysis.suggestedTopic AND originalAnalysis.suggestedTopic IN (
                    'Разработка', 'Дизайн', 'Обучение', 'Работа') THEN 1 ELSE 0 END +
                3 * (SELECT COUNT(*) FROM material_labels sourceLabel
                    JOIN material_labels targetLabel ON targetLabel.labelId = sourceLabel.labelId
                    WHERE sourceLabel.materialId = :materialId AND targetLabel.materialId = candidate.id) AS score
            FROM materials original
            JOIN materials candidate ON candidate.id <> original.id
            LEFT JOIN processing_records originalAnalysis ON originalAnalysis.materialId = original.id
            LEFT JOIN processing_records candidateAnalysis ON candidateAnalysis.materialId = candidate.id
            WHERE original.id = :materialId
        )
        SELECT materials.* FROM materials JOIN signals ON signals.id = materials.id
        WHERE signals.score > 0 ORDER BY signals.score DESC, materials.createdAt DESC, materials.id DESC LIMIT 5
    """)
    fun observeRelated(materialId: Long): Flow<List<Material>>

    @Query(
        """SELECT * FROM materials WHERE id IN (
               SELECT materials_fts.rowid FROM materials_fts WHERE materials_fts MATCH :query
               UNION
               SELECT notes.materialId FROM notes
               JOIN notes_fts ON notes.id = notes_fts.rowid WHERE notes_fts MATCH :query
           ) ORDER BY createdAt DESC, id DESC"""
    )
    fun search(query: String): Flow<List<Material>>
}

@Database(
    entities = [
        Material::class,
        MaterialIndex::class,
        LibraryCollection::class,
        CollectionMembership::class,
        TaxonomyLabel::class,
        MaterialLabel::class,
        MaterialNote::class,
        NoteIndex::class,
        ReadingEvent::class,
        ReadLaterEntry::class,
        ProcessingRecord::class,
    ],
    version = 4,
)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun materials(): MaterialDao
    abstract fun organization(): OrganizationDao
    abstract fun reading(): ReadingDao
    abstract fun processing(): ProcessingDao
}
