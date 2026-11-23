package com.knowledgehub.core

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class CaptureRepository private constructor(
    private val database: LibraryDatabase,
    private val attachments: AttachmentStorage,
) : Closeable {
    private val dao = database.materials()
    val materials: Flow<List<Material>> = dao.observeAll()

    fun observe(id: Long): Flow<Material?> = dao.observe(id)

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    fun search(query: String): Flow<List<Material>> {
        if (query.isBlank()) return materials
        val terms = SEARCH_TERMS.findAll(query).take(16).joinToString(" ") { "\"${it.value.take(80)}*\"" }
        return if (terms.isEmpty()) flowOf(emptyList()) else dao.search(terms)
    }

    fun attachment(material: Material): File? = material.filePath?.let(attachments::find)

    suspend fun save(input: CaptureInput): Material = saveAll(listOf(input)).single()

    suspend fun saveAll(inputs: List<CaptureInput>): List<Material> = withContext(Dispatchers.IO) {
        val staged = mutableListOf<Material>()
        try {
            inputs.forEach { input ->
                staged += when (input) {
                    is CaptureInput.Link -> link(input.value, input.subject)
                    is CaptureInput.Text -> text(input)
                    is CaptureInput.File -> attachments.copy(input)
                }
            }
        } catch (failure: Throwable) {
            discardFiles(staged)
            throw failure
        }
        // Publish all records atomically once their source files are durable.
        withContext(NonCancellable) {
            try {
                database.withTransaction {
                    staged.map { draft ->
                        val duplicateOf = draft.canonicalUrl?.let { dao.firstMatchingId(it) }
                        val material = draft.copy(duplicateOf = duplicateOf)
                        material.copy(id = dao.insert(material))
                    }
                }
            } catch (failure: Throwable) {
                discardFiles(staged)
                throw failure
            }
        }
    }

    private fun discardFiles(materials: List<Material>) {
        materials.forEach { attachment(it)?.delete() }
    }

    private fun text(input: CaptureInput.Text): Material {
        require(input.value.isNotBlank()) { "Empty shared text" }
        val sharedUrl = input.value.takeIf { LinkIdentity.parse(it) != null }
            ?: WEB_URL.findAll(input.value)
                .map { it.value.trimEnd('.', ',', ')', ']', '»', '!', ';') }
                .firstOrNull { LinkIdentity.parse(it) != null }
        if (sharedUrl != null) return link(sharedUrl, input.subject, input.value)
        return Material(
            kind = "TEXT",
            title = displayTitle(input.subject ?: input.value).ifEmpty { displayTitle(input.value) },
            originalText = input.value,
        )
    }

    private fun link(original: String, subject: String?, originalText: String? = null): Material {
        val identity = requireNotNull(LinkIdentity.parse(original)) { "Invalid web address" }
        return Material(
            kind = "LINK",
            title = displayTitle(subject.orEmpty()).ifEmpty { identity.host },
            originalUrl = original,
            originalText = originalText,
            canonicalUrl = identity.canonicalUrl,
            source = identity.host,
        )
    }

    override fun close() = database.close()

    companion object {
        private val SEARCH_TERMS = Regex("[\\p{L}\\p{N}]+")
        private val WEB_URL = Regex("https?://[^\\s<>\"«»]+", RegexOption.IGNORE_CASE)

        private const val DEFAULT_DATABASE = "knowledge-hub.db"
        @Volatile private var sharedInstance: CaptureRepository? = null

        /** Process-lifetime repository. Activity and ViewModel callers do not close it. */
        fun shared(context: Context): CaptureRepository =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: create(context).also { sharedInstance = it }
            }

        fun create(context: Context, databaseName: String = DEFAULT_DATABASE): CaptureRepository {
            val appContext = context.applicationContext
            val database = Room.databaseBuilder(appContext, LibraryDatabase::class.java, databaseName).build()
            return CaptureRepository(database, AttachmentStorage(appContext))
        }
    }
}

internal fun displayTitle(value: String): String = value.trim().lineSequence().first().take(160)
