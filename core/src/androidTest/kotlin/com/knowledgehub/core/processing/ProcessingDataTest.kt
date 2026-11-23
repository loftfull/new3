package com.knowledgehub.core.processing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.knowledgehub.core.CaptureInput
import com.knowledgehub.core.CaptureRepository
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessingDataTest {
    @Test fun providerFailurePreservesOriginalAndSupportsRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = "processing-${UUID.randomUUID()}.db"
        val input = "Практический курс Android на Kotlin с примерами."
        try {
            val id = CaptureRepository.create(context, db).use { repository ->
                val material = repository.save(CaptureInput.Text(input))
                repository.processPending { throw IOException("Injected offline provider failure") }
                assertEquals(ProcessingState.FAILED, repository.processingRecords.first().single().state)
                assertEquals(input, repository.observe(material.id).first()?.originalText)
                material.id
            }
            CaptureRepository.create(context, db).use { repository ->
                assertTrue(repository.retryProcessing(id))
                repository.processPending(LocalMaterialAnalyzer)
                val processed = repository.processingRecords.first().single()
                assertEquals(ProcessingState.COMPLETE, processed.state)
                assertEquals(2, processed.attempts)
                assertEquals("Разработка", processed.suggestedTopic)
                assertEquals(input, repository.search("Практический").first().single().originalText)
                assertFalse(repository.retryProcessing(id))
            }
        } finally {
            context.deleteDatabase(db)
        }
    }
}
