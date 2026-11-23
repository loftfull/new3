package com.knowledgehub.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OrganizationDao {
    @Query("SELECT * FROM library_collections ORDER BY nameKey, id")
    fun observeCollections(): Flow<List<LibraryCollection>>

    @Query("SELECT kind FROM library_collections WHERE id = :id")
    suspend fun collectionKind(id: Long): CollectionKind?

    @Query("SELECT * FROM library_collections WHERE nameKey = :nameKey")
    suspend fun findCollection(nameKey: String): LibraryCollection?

    @Insert
    suspend fun insertCollection(collection: LibraryCollection): Long

    @Query(
        """SELECT materials.* FROM library_collections
           JOIN collection_memberships ON collection_memberships.collectionId = library_collections.id
           JOIN materials ON materials.id = collection_memberships.materialId
           WHERE library_collections.id = :id AND library_collections.kind IN ('MANUAL', 'PROJECT')
           UNION ALL
           SELECT materials.* FROM library_collections
           JOIN materials ON materials.source = library_collections.source
           WHERE library_collections.id = :id AND library_collections.kind = 'SMART_SOURCE'
           ORDER BY createdAt DESC, id DESC"""
    )
    fun observeCollectionMaterials(id: Long): Flow<List<Material>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(membership: CollectionMembership)

    @Query("DELETE FROM collection_memberships WHERE collectionId = :collectionId AND materialId = :materialId")
    suspend fun removeMembership(collectionId: Long, materialId: Long)

    @Query(
        """SELECT materials.* FROM materials
           WHERE (:source IS NULL OR materials.source = :source)
             AND (NOT :favoritesOnly OR materials.favorite = 1)
             AND (:topicId IS NULL OR EXISTS (
                 SELECT 1 FROM material_labels JOIN taxonomy_labels ON taxonomy_labels.id = material_labels.labelId
                 WHERE material_labels.materialId = materials.id AND labelId = :topicId AND taxonomy_labels.kind = 'TOPIC'
             ))
             AND (:tagId IS NULL OR EXISTS (
                 SELECT 1 FROM material_labels JOIN taxonomy_labels ON taxonomy_labels.id = material_labels.labelId
                 WHERE material_labels.materialId = materials.id AND labelId = :tagId AND taxonomy_labels.kind = 'TAG'
             ))
           ORDER BY CASE WHEN :oldestFirst THEN createdAt END, CASE WHEN :oldestFirst THEN id END,
                    createdAt DESC, id DESC"""
    )
    fun observeFilteredMaterials(
        source: String?,
        topicId: Long?,
        tagId: Long?,
        favoritesOnly: Boolean,
        oldestFirst: Boolean,
    ): Flow<List<Material>>

    @Query("SELECT DISTINCT source FROM materials WHERE source IS NOT NULL ORDER BY source")
    fun observeSources(): Flow<List<String>>

    @Query("SELECT * FROM taxonomy_labels WHERE kind = :kind ORDER BY nameKey, id")
    fun observeLabels(kind: LabelKind): Flow<List<TaxonomyLabel>>

    @Query("SELECT * FROM taxonomy_labels WHERE kind = :kind AND nameKey = :nameKey")
    suspend fun findLabel(kind: LabelKind, nameKey: String): TaxonomyLabel?

    @Insert
    suspend fun insertLabel(label: TaxonomyLabel): Long

    @Query(
        """SELECT taxonomy_labels.* FROM taxonomy_labels
           JOIN material_labels ON material_labels.labelId = taxonomy_labels.id
           WHERE material_labels.materialId = :materialId
           ORDER BY kind, nameKey, id"""
    )
    fun observeMaterialLabels(materialId: Long): Flow<List<TaxonomyLabel>>

    @Query(
        """SELECT materials.* FROM materials JOIN material_labels ON material_labels.materialId = materials.id
           WHERE material_labels.labelId = :labelId ORDER BY createdAt DESC, id DESC"""
    )
    fun observeLabelMaterials(labelId: Long): Flow<List<Material>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMaterialLabel(label: MaterialLabel)

    @Query("DELETE FROM material_labels WHERE materialId = :materialId AND labelId = :labelId")
    suspend fun removeMaterialLabel(materialId: Long, labelId: Long)
}
