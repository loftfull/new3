package com.knowledgehub.core

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.knowledgehub.core.processing.MIGRATION_3_4
import com.knowledgehub.core.processing.MaterialAnalyzer
import com.knowledgehub.core.processing.ProcessingRecord
import com.knowledgehub.core.processing.ProcessingState
import java.io.Closeable
import java.io.File
import java.io.Writer
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CaptureRepository private constructor(
    private val database: LibraryDatabase,
    private val attachments: AttachmentStorage,
) : Closeable {
    private val dao = database.materials()
    private val organization = database.organization()
    private val reading = database.reading()
    private val processing = database.processing()
    private val processingLock = Mutex()
    val materials: Flow<List<Material>> = dao.observeAll()
    val processingRecords: Flow<List<ProcessingRecord>> = processing.observeAll()

    suspend fun exportLibrary(output: Writer) {
        val (materials, notes) = database.withTransaction {
            dao.exportMaterials() to reading.exportNotes()
        }
        withContext(Dispatchers.IO) { writeLibraryExport(output, materials, notes) }
    }

    suspend fun processPending(analyzer: MaterialAnalyzer) = processingLock.withLock {
        while (true) {
            val batch = processing.pendingMaterials(24)
            if (batch.isEmpty()) break
            for (material in batch) process(material, analyzer)
        }
    }

    suspend fun retryProcessing(materialId: Long): Boolean =
        processing.retry(materialId, System.currentTimeMillis()) > 0

    private suspend fun process(material: Material, analyzer: MaterialAnalyzer) {
        val attempts = (processing.find(material.id)?.attempts ?: 0) + 1
        val started = ProcessingRecord(material.id, ProcessingState.RUNNING, attempts, updateTime = System.currentTimeMillis())
        processing.put(started)
        val outcome = try {
            val analysis = analyzer.analyze(material)
            started.copy(
                state = ProcessingState.COMPLETE,
                summary = analysis.summary,
                suggestedTopic = analysis.suggestedTopic,
                keywordText = analysis.keywords.takeIf { it.isNotEmpty() }
                    ?.joinToString(ProcessingRecord.SEPARATOR.toString()),
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            started.copy(state = ProcessingState.FAILED)
        }
        processing.put(outcome.copy(updateTime = System.currentTimeMillis()))
    }

    val collections: Flow<List<LibraryCollection>> = organization.observeCollections()
    val sources: Flow<List<String>> = organization.observeSources()
    val readLaterMaterials: Flow<List<Material>> = reading.observeReadLater()

    fun notes(materialId: Long): Flow<List<MaterialNote>> = reading.observeNotes(materialId)

    suspend fun createNote(materialId: Long, body: String): MaterialNote {
        require(body.isNotBlank()) { "Note body is empty" }
        val time = System.currentTimeMillis()
        val note = MaterialNote(materialId = materialId, body = body, revision = 1, createdAt = time, updatedAt = time)
        return note.copy(id = reading.insertNote(note))
    }

    suspend fun editNote(id: Long, expectedRevision: Long, body: String): NoteEditResult {
        require(body.isNotBlank()) { "Note body is empty" }
        require(expectedRevision > 0) { "Invalid note revision" }
        return database.withTransaction {
            val changed = reading.editNote(id, expectedRevision, body, System.currentTimeMillis())
            val note = reading.findNote(id) ?: return@withTransaction NoteEditResult.Missing
            if (changed > 0) NoteEditResult.Saved(note) else NoteEditResult.Conflict(note)
        }
    }

    fun readingHistory(before: ReadingEvent? = null, limit: Int = 100): Flow<List<ReadingEvent>> {
        require(limit in 1..200) { "Invalid reading history page size" }
        return reading.observeHistory(before?.openedAt, before?.id, limit)
    }

    suspend fun recordReading(materialId: Long) {
        reading.insertEvent(ReadingEvent(materialId = materialId, openedAt = System.currentTimeMillis()))
    }

    fun isReadLater(materialId: Long): Flow<Boolean> = reading.observeReadLaterMembership(materialId)

    suspend fun setReadLater(materialId: Long, included: Boolean) {
        if (included) {
            reading.addReadLater(ReadLaterEntry(materialId, addedAt = System.currentTimeMillis()))
        } else {
            reading.removeReadLater(materialId)
        }
    }


    fun collectionMaterials(id: Long): Flow<List<Material>> = organization.observeCollectionMaterials(id)

    fun filteredMaterials(filter: LibraryFilter): Flow<List<Material>> =
        organization.observeFilteredMaterials(
            source = filter.source?.let(::sourceHost),
            topicId = filter.topicId,
            tagId = filter.tagId,
            favoritesOnly = filter.favoritesOnly,
            oldestFirst = filter.order == LibraryOrder.OLDEST_FIRST,
        )

    fun labels(kind: LabelKind): Flow<List<TaxonomyLabel>> = organization.observeLabels(kind)

    fun materialLabels(materialId: Long): Flow<List<TaxonomyLabel>> =
        organization.observeMaterialLabels(materialId)

    fun labelMaterials(labelId: Long): Flow<List<Material>> = organization.observeLabelMaterials(labelId)

    suspend fun createManualCollection(name: String): LibraryCollection =
        createCollection(name, CollectionKind.MANUAL, null)

    suspend fun createSourceCollection(name: String, source: String): LibraryCollection =
        createCollection(name, CollectionKind.SMART_SOURCE, sourceHost(source))

    suspend fun setCollectionMembership(collectionId: Long, materialId: Long, included: Boolean) {
        database.withTransaction {
            require(organization.collectionKind(collectionId) == CollectionKind.MANUAL) {
                "Manual collection required"
            }
            if (included) {
                organization.insertMembership(CollectionMembership(collectionId, materialId))
            } else {
                organization.removeMembership(collectionId, materialId)
            }
        }
    }

    suspend fun getOrCreateLabel(kind: LabelKind, name: String): TaxonomyLabel {
        val displayName = validatedName(name)
        val key = nameKey(displayName)
        return database.withTransaction {
            organization.findLabel(kind, key) ?: TaxonomyLabel(kind = kind, name = displayName, nameKey = key)
                .let { it.copy(id = organization.insertLabel(it)) }
        }
    }

    suspend fun setMaterialLabel(materialId: Long, labelId: Long, applied: Boolean) {
        if (applied) {
            organization.insertMaterialLabel(MaterialLabel(materialId, labelId))
        } else {
            organization.removeMaterialLabel(materialId, labelId)
        }
    }

    private suspend fun createCollection(name: String, kind: CollectionKind, source: String?): LibraryCollection {
        val displayName = validatedName(name)
        val key = nameKey(displayName)
        return database.withTransaction {
            require(organization.findCollection(key) == null) { "Collection name already exists" }
            val collection = LibraryCollection(name = displayName, nameKey = key, kind = kind, source = source)
            collection.copy(id = organization.insertCollection(collection))
        }
    }

    fun observe(id: Long): Flow<Material?> = dao.observe(id)

    fun relatedMaterials(id: Long): Flow<List<Material>> = dao.observeRelated(id)

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
            val database = Room.databaseBuilder(appContext, LibraryDatabase::class.java, databaseName)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
            return CaptureRepository(database, AttachmentStorage(appContext))
        }
    }
}

internal fun displayTitle(value: String): String = value.trim().lineSequence().first().take(160)


private fun validatedName(value: String): String = value.trim().also {
    require(it.isNotEmpty() && it.length <= 120 && it.none(Char::isISOControl)) { "Invalid name" }
}

private fun nameKey(name: String): String = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFC)


private fun sourceHost(value: String): String {
    val input = value.trim()
    val identity = requireNotNull(LinkIdentity.parse("https://$input")) { "Invalid source" }
    require(input.none { it in "/?#" } && (!input.contains(':') || input.endsWith(']'))) {
        "Source must be a domain"
    }
    return identity.host
}
