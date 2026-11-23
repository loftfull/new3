package com.knowledgehub.core

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_materials_source ON materials (source)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS library_collections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                nameKey TEXT NOT NULL,
                kind TEXT NOT NULL,
                source TEXT
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_library_collections_nameKey ON library_collections (nameKey)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS collection_memberships (
                collectionId INTEGER NOT NULL,
                materialId INTEGER NOT NULL,
                PRIMARY KEY (collectionId, materialId),
                FOREIGN KEY (collectionId) REFERENCES library_collections (id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY (materialId) REFERENCES materials (id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_memberships_materialId ON collection_memberships (materialId)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS taxonomy_labels (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                kind TEXT NOT NULL,
                name TEXT NOT NULL,
                nameKey TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_taxonomy_labels_kind_nameKey ON taxonomy_labels (kind, nameKey)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS material_labels (
                materialId INTEGER NOT NULL,
                labelId INTEGER NOT NULL,
                PRIMARY KEY (materialId, labelId),
                FOREIGN KEY (materialId) REFERENCES materials (id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY (labelId) REFERENCES taxonomy_labels (id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_material_labels_labelId ON material_labels (labelId)")
    }
}
