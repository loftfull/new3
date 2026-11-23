package com.knowledgehub.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelatedMaterialTest {
    @Test fun sameSourceAppearsInRelatedMaterialsWithoutChangingOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = "related-${UUID.randomUUID()}.db"
        try {
            CaptureRepository.create(context, db).use { repository ->
                val original = repository.save(CaptureInput.Link("https://example.org/notes"))
                val related = repository.save(CaptureInput.Link("https://example.org/topics"))
                repository.save(CaptureInput.Link("https://other.example/articles"))
                assertEquals(listOf(related.id), repository.relatedMaterials(original.id).first().map { it.id })
                assertEquals(original.originalUrl, repository.observe(original.id).first()?.originalUrl)
            }
        } finally {
            context.deleteDatabase(db)
        }
        Unit
    }
}
