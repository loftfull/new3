package com.knowledgehub.core

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                materialId INTEGER NOT NULL,
                body TEXT NOT NULL,
                revision INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY (materialId) REFERENCES materials (id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_materialId ON notes (materialId)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(`body` TEXT NOT NULL, tokenize=unicode61, content=`notes`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS reading_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                materialId INTEGER NOT NULL,
                openedAt INTEGER NOT NULL,
                FOREIGN KEY (materialId) REFERENCES materials (id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_events_materialId ON reading_events (materialId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_events_openedAt_id ON reading_events (openedAt, id)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS read_later (
                materialId INTEGER PRIMARY KEY NOT NULL,
                addedAt INTEGER NOT NULL,
                FOREIGN KEY (materialId) REFERENCES materials (id) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
    }
}
