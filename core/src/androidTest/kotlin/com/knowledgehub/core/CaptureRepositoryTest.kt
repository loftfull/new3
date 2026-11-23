package com.knowledgehub.core

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureRepositoryTest {
    @Test
    fun sharePersistsOriginalAndKeepsBothExactDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "capture-test-${UUID.randomUUID()}.db"
        val sharedText = "Справочник https://EXAMPLE.org:443/guide"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, sharedText)
        try {
            val original = CaptureRepository.create(context, databaseName).use { repository ->
                val first = repository.saveAll(ShareCapture.fromIntent(intent)).single()
                val second = repository.save(CaptureInput.Link("https://example.org/guide"))
                assertNotEquals(first.id, second.id)
                assertEquals(first.id, second.duplicateOf)
                repository.setFavorite(first.id, true)
                first
            }
            CaptureRepository.create(context, databaseName).use { repository ->
                val all = repository.materials.first()
                assertEquals(2, all.size)
                val persisted = all.single { it.id == original.id }
                assertEquals(sharedText, persisted.originalText)
                assertEquals(true, persisted.favorite)
                assertEquals("https://EXAMPLE.org:443/guide", original.originalUrl)
                assertEquals(listOf(original.id), repository.search("Справ").first().map { it.id })
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun invalidAttachmentInShareBatchDoesNotPublishPartialRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "capture-test-${UUID.randomUUID()}.db"
        try {
            CaptureRepository.create(context, databaseName).use { repository ->
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    clipData = ClipData.newPlainText("Text", "Оригинальная заметка")
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        arrayListOf(Uri.parse("file:///invalid/test.pdf")),
                    )
                }
                val inputs = ShareCapture.fromIntent(intent)
                assertEquals(2, inputs.size)
                try {
                    repository.saveAll(inputs)
                    fail("Unsupported file:// URI must be rejected")
                } catch (_: IllegalArgumentException) {
                    assertEquals(emptyList<Material>(), repository.materials.first())
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun attachmentRemainsAvailableAfterOriginalProviderDeletesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val databaseName = "capture-test-${UUID.randomUUID()}.db"
        val expected = "Данные сохраняются локально".encodeToByteArray()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "capture-test-${UUID.randomUUID()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        var attachment: java.io.File? = null
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(expected) }
            val id = CaptureRepository.create(context, databaseName).use { repository ->
                val entry = repository.save(CaptureInput.File(uri))
                attachment = repository.attachment(entry)
                assertEquals("text/plain", entry.mimeType)
                entry.id
            }
            resolver.delete(uri, null, null)
            CaptureRepository.create(context, databaseName).use { repository ->
                val entry = checkNotNull(repository.observe(id).first())
                assertArrayEquals(expected, checkNotNull(repository.attachment(entry)).readBytes())
            }
        } finally {
            resolver.delete(uri, null, null)
            attachment?.delete()
            context.deleteDatabase(databaseName)
        }
    }
}
