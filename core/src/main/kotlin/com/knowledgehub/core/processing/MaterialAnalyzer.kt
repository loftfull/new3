package com.knowledgehub.core.processing

import com.knowledgehub.core.Material
import java.util.Locale

fun interface MaterialAnalyzer {
    suspend fun analyze(material: Material): MaterialAnalysis
}

data class MaterialAnalysis(
    val summary: String?,
    val suggestedTopic: String,
    val keywords: List<String>,
)

object LocalMaterialAnalyzer : MaterialAnalyzer {
    private val links = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val sentences = Regex("(?<=[.!?])\\s+|\\n+")
    private val words = Regex("[\\p{L}][\\p{L}\\p{N}]{2,}")
    private val spaces = Regex("\\s+")
    private val stop = setOf("this", "that", "with", "from", "the", "for", "and", "are", "have",
        "что", "это", "как", "для", "или", "его", "при", "этот", "этого", "можно", "свои")
    private val topics = linkedMapOf(
        "Разработка" to listOf("kotlin", "android", "python", "программ", "разработ", "api"),
        "Дизайн" to listOf("дизайн", "типограф", "интерфейс", "figma", "design", "ux"),
        "Обучение" to listOf("обучен", "учеб", "курс", "lesson", "learn", "study"),
        "Работа" to listOf("проект", "планирован", "бизнес", "project", "business"),
    )

    override suspend fun analyze(material: Material): MaterialAnalysis {
        val body = links.replace(material.originalText.orEmpty().take(20_000), "").trim()
        val keywords = words.findAll("${material.title} $body".lowercase(Locale.ROOT))
            .map { it.value.take(48) }.filterNot { it in stop }
            .groupingBy { it }.eachCount()
        val topic = topics.entries.maxByOrNull { (_, roots) ->
            keywords.keys.count { term -> roots.any(term::startsWith) }
        }?.takeIf { (_, roots) -> keywords.keys.any { term -> roots.any(term::startsWith) } }?.key
            ?: defaultTopic(material)
        val summary = summarize(body)
        val tags = keywords.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5).map { it.key }
        return MaterialAnalysis(summary, topic, tags)
    }

    private fun summarize(body: String): String? {
        if (body.isBlank()) return null
        val selected = sentences.splitToSequence(body)
            .map { spaces.replace(it, " ").trim() }
            .filter { it.length >= 24 }
            .take(3)
            .joinToString(" ")
        val content = selected.ifBlank { spaces.replace(body, " ") }
        return if (content.length <= 440) content else content.take(440).substringBeforeLast(' ').trimEnd() + "…"
    }

    private fun defaultTopic(material: Material): String {
        val source = material.source.orEmpty()
        return when {
            source == "youtu.be" || source == "youtube.com" || source.endsWith(".youtube.com") -> "Видео"
            material.mimeType == "application/pdf" -> "Документы"
            material.kind == "FILE" -> "Файлы"
            material.kind == "LINK" -> "Ссылки"
            else -> "Заметки"
        }
    }
}
