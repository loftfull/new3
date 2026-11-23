package com.knowledgehub.core

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "materials", indices = [Index("canonicalUrl"), Index("source")])
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val originalUrl: String? = null,
    val originalText: String? = null,
    val canonicalUrl: String? = null,
    val source: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "SAVED",
    val fileName: String? = null,
    val filePath: String? = null,
    val mimeType: String? = null,
    val duplicateOf: Long? = null,
    val favorite: Boolean = false,
)

@Fts4(contentEntity = Material::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "materials_fts")
internal data class MaterialIndex(
    val title: String,
    val originalUrl: String?,
    val originalText: String?,
    val fileName: String?,
    val source: String?,
)
