package com.knowledgehub.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.StringWriter
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryExportTest {
    @Test fun exportsOriginalLinkAndCurrentNoteWithoutChangingLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = "export-${UUID.randomUUID()}.db"
        val link = "https://example.org/справочник?section=заметки"
        val note = "Проверить источники и «цитаты»."
        try {
            CaptureRepository.create(context, db).use { repository ->
                val item = repository.save(CaptureInput.Link(link, "Справочник"))
                repository.createNote(item.id, note)
                val buffer = StringWriter()
                repository.exportLibrary(buffer)
                val exported = JSONObject(buffer.toString()).getJSONArray("materials").getJSONObject(0)
                assertEquals(link, exported.getString("originalUrl"))
                assertEquals(note, exported.getJSONArray("notes").getJSONObject(0).getString("body"))
                assertEquals(link, repository.observe(item.id).first()?.originalUrl)
                assertEquals(note, repository.notes(item.id).first().single().body)
            }
        } finally {
            context.deleteDatabase(db)
        }
        Unit
    }
}
