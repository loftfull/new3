package com.knowledgehub.core.processing

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.knowledgehub.core.Material
import kotlinx.coroutines.flow.Flow

enum class ProcessingState { QUEUED, RUNNING, COMPLETE, FAILED }

@Entity(
    tableName = "processing_records",
    foreignKeys = [ForeignKey(
        entity = Material::class,
        parentColumns = ["id"],
        childColumns = ["materialId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ProcessingRecord(
    @PrimaryKey val materialId: Long,
    val state: ProcessingState,
    val attempts: Int,
    val summary: String? = null,
    val suggestedTopic: String? = null,
    val keywordText: String? = null,
    val updateTime: Long,
) {
    val keywords: List<String> get() = keywordText?.split(SEPARATOR).orEmpty()

    internal companion object {
        const val SEPARATOR = '\u001F'
    }
}

@Dao
internal interface ProcessingDao {
    @Query("SELECT * FROM processing_records")
    fun observeAll(): Flow<List<ProcessingRecord>>

    @Query("SELECT * FROM processing_records WHERE materialId = :id")
    suspend fun find(id: Long): ProcessingRecord?

    @Query("""
        SELECT materials.* FROM materials LEFT JOIN processing_records ON materials.id = processing_records.materialId
        WHERE processing_records.state IS NULL OR processing_records.state IN ('QUEUED', 'RUNNING')
        ORDER BY materials.id LIMIT :limit
    """)
    suspend fun pendingMaterials(limit: Int): List<Material>

    @Upsert
    suspend fun put(record: ProcessingRecord)

    @Query("UPDATE processing_records SET state = 'QUEUED', updateTime = :time WHERE materialId = :id AND state = 'FAILED'")
    suspend fun retry(id: Long, time: Long): Int
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS processing_records (
                materialId INTEGER NOT NULL,
                state TEXT NOT NULL,
                attempts INTEGER NOT NULL,
                summary TEXT,
                suggestedTopic TEXT,
                keywordText TEXT,
                updateTime INTEGER NOT NULL,
                PRIMARY KEY(materialId),
                FOREIGN KEY(materialId) REFERENCES materials(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
