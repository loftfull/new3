package com.knowledgehub.core

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDataTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val migration = MigrationTestHelper(instrumentation, LibraryDatabase::class.java)

    @Test
    fun versionTwoOpensWithOriginalsCollectionsLabelsAndSearchIntact() = runBlocking {
        val name = "reading-migration-${UUID.randomUUID()}.db"
        try {
            migration.createDatabase(name, 2).use { db ->
                db.execSQL(
                    """INSERT INTO materials (id, kind, title, originalText, createdAt, status, favorite)
                       VALUES (9, 'TEXT', 'Архив', 'Оригинал', 100, 'SAVED', 1)"""
                )
                db.execSQL(
                    """INSERT INTO library_collections (id, name, nameKey, kind)
                       VALUES (15, 'Подборка', 'подборка', 'MANUAL')"""
                )
                db.execSQL("INSERT INTO collection_memberships (collectionId, materialId) VALUES (15, 9)")
                db.execSQL(
                    """INSERT INTO taxonomy_labels (id, kind, name, nameKey)
                       VALUES (16, 'TOPIC', 'Архив', 'архив')"""
                )
                db.execSQL("INSERT INTO material_labels (materialId, labelId) VALUES (9, 16)")
            }
            CaptureRepository.create(context, name).use { repository ->
                val material = repository.collectionMaterials(15).first().single()
                assertEquals(9L, material.id)
                assertEquals("Оригинал", material.originalText)
                assertTrue(material.favorite)
                assertEquals(listOf(16L), repository.materialLabels(9).first().map { it.id })
                assertEquals(listOf(9L), repository.search("Арх").first().map { it.id })
                assertTrue(repository.notes(9).first().isEmpty())
                repository.createNote(9, "Первая заметка")
                assertEquals(listOf(9L), repository.search("замет").first().map { it.id })
            }
            migration.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun notesDetectStaleEditsAndReadingStateSurvivesReopen() = runBlocking {
        val name = "reading-state-${UUID.randomUUID()}.db"
        try {
            val (materialId, noteId) = CaptureRepository.create(context, name).use { repository ->
                val material = repository.save(CaptureInput.Link("https://example.org/reader"))
                val note = repository.createNote(material.id, "Черновая формулировка")
                val updated = repository.editNote(note.id, note.revision, "  Изучение кириллицы  ")
                    as NoteEditResult.Saved
                val stale = repository.editNote(note.id, note.revision, "Устаревшая правка")
                    as NoteEditResult.Conflict
                assertEquals(updated.note, stale.current)
                assertEquals(note.revision + 1, updated.note.revision)
                assertEquals(NoteEditResult.Missing, repository.editNote(Long.MAX_VALUE, 1, "Правка"))
                assertTrue(repository.search("Чернов").first().isEmpty())
                assertEquals(listOf(material.id), repository.search("кирил").first().map { it.id })
                repository.recordReading(material.id)
                repository.recordReading(material.id)
                repository.setReadLater(material.id, true)
                repository.setReadLater(material.id, true)
                material.id to note.id
            }
            CaptureRepository.create(context, name).use { repository ->
                val note = repository.notes(materialId).first().single()
                assertEquals(noteId, note.id)
                assertEquals("  Изучение кириллицы  ", note.body)
                val history = repository.readingHistory().first()
                assertEquals(listOf(materialId, materialId), history.map { it.materialId })
                assertEquals(listOf(history[1]), repository.readingHistory(before = history[0], limit = 1).first())
                assertTrue(repository.isReadLater(materialId).first())
                assertEquals(listOf(materialId), repository.readLaterMaterials.first().map { it.id })
                repository.setReadLater(materialId, false)
                assertTrue(repository.readLaterMaterials.first().isEmpty())
                assertEquals("https://example.org/reader", repository.observe(materialId).first()?.originalUrl)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun concurrentEditsAgainstTheSameRevisionProduceOneSaveAndOneConflict() = runBlocking {
        val name = "reading-concurrency-${UUID.randomUUID()}.db"
        try {
            CaptureRepository.create(context, name).use { repository ->
                val material = repository.save(CaptureInput.Text("Исходный материал"))
                val note = repository.createNote(material.id, "Исходная заметка")
                val results = coroutineScope {
                    val first = async { repository.editNote(note.id, note.revision, "Первая правка") }
                    val second = async { repository.editNote(note.id, note.revision, "Вторая правка") }
                    listOf(first.await(), second.await())
                }
                val saved = results.filterIsInstance<NoteEditResult.Saved>().single()
                val conflict = results.filterIsInstance<NoteEditResult.Conflict>().single()
                assertEquals(saved.note, conflict.current)
                assertEquals(saved.note, repository.notes(material.id).first().single())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
