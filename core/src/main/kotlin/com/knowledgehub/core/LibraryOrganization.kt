package com.knowledgehub.core

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CollectionKind { MANUAL, SMART_SOURCE }
enum class LabelKind { TOPIC, TAG }
enum class LibraryOrder { NEWEST_FIRST, OLDEST_FIRST }

data class LibraryFilter(
    val source: String? = null,
    val topicId: Long? = null,
    val tagId: Long? = null,
    val favoritesOnly: Boolean = false,
    val order: LibraryOrder = LibraryOrder.NEWEST_FIRST,
)

@Entity(tableName = "library_collections", indices = [Index(value = ["nameKey"], unique = true)])
data class LibraryCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    internal val nameKey: String,
    val kind: CollectionKind,
    val source: String?,
)

@Entity(
    tableName = "collection_memberships",
    primaryKeys = ["collectionId", "materialId"],
    foreignKeys = [
        ForeignKey(entity = LibraryCollection::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Material::class, parentColumns = ["id"], childColumns = ["materialId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("materialId")],
)
internal data class CollectionMembership(val collectionId: Long, val materialId: Long)

@Entity(tableName = "taxonomy_labels", indices = [Index(value = ["kind", "nameKey"], unique = true)])
data class TaxonomyLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LabelKind,
    val name: String,
    internal val nameKey: String,
)

@Entity(
    tableName = "material_labels",
    primaryKeys = ["materialId", "labelId"],
    foreignKeys = [
        ForeignKey(entity = Material::class, parentColumns = ["id"], childColumns = ["materialId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaxonomyLabel::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("labelId")],
)
internal data class MaterialLabel(val materialId: Long, val labelId: Long)
