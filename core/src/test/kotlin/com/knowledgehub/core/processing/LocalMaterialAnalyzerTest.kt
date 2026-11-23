package com.knowledgehub.core.processing

import com.knowledgehub.core.Material
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMaterialAnalyzerTest {
    @Test fun summarizesAvailableTextAndSuggestsTopic() = runBlocking {
        val text = "Типографика помогает выстроить читаемый интерфейс. Дизайн требует ясной иерархии. https://example.org/"
        val result = LocalMaterialAnalyzer.analyze(Material(kind = "TEXT", title = "Типографика", originalText = text))
        assertEquals("Дизайн", result.suggestedTopic)
        assertTrue(result.summary.orEmpty().contains("Типографика помогает"))
        assertFalse(result.summary.orEmpty().contains("example.org"))
        assertTrue("типографика" in result.keywords)
    }

    @Test fun linkOnlyGetsClassificationWithoutInventedSummary() = runBlocking {
        val result = LocalMaterialAnalyzer.analyze(Material(
            kind = "LINK",
            title = "youtu.be",
            source = "youtu.be",
            originalText = "https://youtu.be/test",
        ))
        assertEquals("Видео", result.suggestedTopic)
        assertNull(result.summary)
    }
}
