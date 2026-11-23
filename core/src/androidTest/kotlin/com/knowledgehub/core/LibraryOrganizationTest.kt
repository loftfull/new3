package com.knowledgehub.core

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryOrganizationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val migration = MigrationTestHelper(instrumentation, LibraryDatabase::class.java)

    @Test
    fun upgradePreservesOriginalsAttachmentsFavoritesDuplicatesAndFts() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        val attachmentDirectory = File(context.filesDir, "attachments").apply { mkdirs() }
        val originalAttachment = File(attachmentDirectory, UUID.randomUUID().toString())
        val bytes = "Оригинальные данные".encodeToByteArray()
        try {
            originalAttachment.writeBytes(bytes)
            migration.createDatabase(name, 1).use { db ->
                db.execSQL(
                    """INSERT INTO materials
                       (id, kind, title, originalUrl, originalText, canonicalUrl, source, createdAt, status, favorite)
                       VALUES (7, 'LINK', 'Справочник', 'https://EXAMPLE.org:443/a', 'Исходный текст',
                               'https://example.org/a', 'example.org', 123, 'SAVED', 1)"""
                )
                db.execSQL(
                    """INSERT INTO materials
                       (id, kind, title, originalUrl, canonicalUrl, source, createdAt, status, duplicateOf, favorite)
                       VALUES (8, 'LINK', 'Копия', 'https://example.org/a', 'https://example.org/a',
                               'example.org', 124, 'SAVED', 7, 0)"""
                )
                db.execSQL(
                    """INSERT INTO materials
                       (id, kind, title, createdAt, status, fileName, filePath, mimeType, favorite)
                       VALUES (9, 'FILE', 'Заметки', 125, 'SAVED', 'notes.pdf', ?, 'application/pdf', 0)""",
                    arrayOf(originalAttachment.name),
                )
            }
            migration.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
            CaptureRepository.create(context, name).use { repository ->
                val all = repository.materials.first().associateBy { it.id }
                assertEquals(setOf(7L, 8L, 9L), all.keys)
                assertEquals("https://EXAMPLE.org:443/a", all.getValue(7).originalUrl)
                assertEquals("Исходный текст", all.getValue(7).originalText)
                assertTrue(all.getValue(7).favorite)
                assertEquals(7L, all.getValue(8).duplicateOf)
                assertArrayEquals(bytes, checkNotNull(repository.attachment(all.getValue(9))).readBytes())
                assertEquals(listOf(7L), repository.search("Справ").first().map { it.id })
                val smart = repository.createSourceCollection("Источник", "EXAMPLE.ORG")
                assertEquals(listOf(8L, 7L), repository.collectionMaterials(smart.id).first().map { it.id })
            }
        } finally {
            originalAttachment.delete()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun manualAndSmartCollectionsAndTypedLabelsPersistWithoutChangingMaterials() = runBlocking {
        val name = "organization-${UUID.randomUUID()}.db"
        try {
            val ids = CaptureRepository.create(context, name).use { repository ->
                val article = repository.save(CaptureInput.Link("https://example.org/a"))
                val unrelated = repository.save(CaptureInput.Link("https://unrelated.org/a"))
                val manual = repository.createManualCollection(" Чтение ")
                val smart = repository.createSourceCollection("Пример", "EXAMPLE.ORG")
                val topic = repository.getOrCreateLabel(LabelKind.TOPIC, "Дизайн")
                val tag = repository.getOrCreateLabel(LabelKind.TAG, "Дизайн")
                assertEquals(topic.id, repository.getOrCreateLabel(LabelKind.TOPIC, " дизайн ").id)
                assertTrue(topic.id != tag.id)
                repository.setCollectionMembership(manual.id, unrelated.id, included = true)
                repository.setCollectionMembership(manual.id, unrelated.id, included = true)
                repository.setMaterialLabel(article.id, topic.id, applied = true)
                repository.setMaterialLabel(article.id, tag.id, applied = true)
                try {
                    repository.setCollectionMembership(smart.id, unrelated.id, included = true)
                    fail("Smart collections cannot accept manual membership")
                } catch (_: IllegalArgumentException) {
                    assertEquals(listOf(article.id), repository.collectionMaterials(smart.id).first().map { it.id })
                }
                listOf(article.id, unrelated.id, manual.id, smart.id, topic.id, tag.id)
            }
            val (article, unrelated, manual, smart) = ids
            val topic = ids[4]
            val tag = ids[5]
            CaptureRepository.create(context, name).use { repository ->
                assertEquals(setOf(manual, smart), repository.collections.first().map { it.id }.toSet())
                assertEquals(listOf(unrelated), repository.collectionMaterials(manual).first().map { it.id })
                val second = repository.save(CaptureInput.Link("https://example.org/b"))
                assertEquals(setOf(second.id, article), repository.collectionMaterials(smart).first().map { it.id }.toSet())
                assertEquals(listOf("example.org", "unrelated.org"), repository.sources.first())
                assertEquals(setOf(topic, tag), repository.materialLabels(article).first().map { it.id }.toSet())
                assertEquals(listOf(article), repository.labelMaterials(topic).first().map { it.id })
                val ascending = LibraryFilter(source = "EXAMPLE.ORG", order = LibraryOrder.OLDEST_FIRST)
                assertEquals(listOf(article, second.id), repository.filteredMaterials(ascending).first().map { it.id })
                repository.setFavorite(article, true)
                val combined = LibraryFilter(source = "example.org", topicId = topic, tagId = tag, favoritesOnly = true)
                assertEquals(listOf(article), repository.filteredMaterials(combined).first().map { it.id })
                assertTrue(repository.filteredMaterials(combined.copy(topicId = tag)).first().isEmpty())
                repository.setMaterialLabel(article, tag, applied = false)
                repository.setCollectionMembership(manual, unrelated, included = false)
                assertEquals(listOf(topic), repository.materialLabels(article).first().map { it.id })
                assertTrue(repository.collectionMaterials(manual).first().isEmpty())
                assertEquals(setOf(article, unrelated, second.id), repository.materials.first().map { it.id }.toSet())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
