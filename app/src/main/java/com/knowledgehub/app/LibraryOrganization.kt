package com.knowledgehub.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knowledgehub.core.CollectionKind
import com.knowledgehub.core.LibraryCollection
import com.knowledgehub.core.Material
import com.knowledgehub.core.NoteEditResult

@Composable
internal fun CollectionLibrary(
    model: LibraryModel,
    materials: List<Material>,
    modifier: Modifier,
    openMaterial: (Long) -> Unit,
) {
    val repo = model.repository
    val collections by repo.collections.collectAsStateWithLifecycle(emptyList())
    val sources by repo.sources.collectAsStateWithLifecycle(emptyList())
    val readLater by repo.readLaterMaterials.collectAsStateWithLifecycle(emptyList())
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var systemList by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val clear = { selected = null; systemList = null; creating = false }
    BackHandler(selected != null || systemList != null || creating, onBack = clear)
    val entry = collections.firstOrNull { it.id == selected }
    if (entry != null) {
        CollectionContents(model, entry, materials, modifier, openMaterial, clear)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val group = systemList
        if (group != null) {
            item { TextButton(onClick = clear) { Text("К коллекциям") } }
            item { Text(group, style = MaterialTheme.typography.headlineSmall) }
            val list = if (group == "Избранное") materials.filter { it.favorite } else readLater
            if (list.isEmpty()) item { Text("В этой подборке пока нет материалов.") }
            items(list, key = { it.id }) { OpenMaterialRow(it, openMaterial) }
        } else {
            item { Button(onClick = { creating = !creating }) { Text(if (creating) "Закрыть редактор" else "Создать коллекцию") } }
            if (creating) item { CollectionEditor(model, sources) { id -> creating = false; selected = id } }
            item {
                val favorites = materials.count { it.favorite }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { systemList = "Избранное" }) { Text("Избранное · $favorites") }
                    OutlinedButton(onClick = { systemList = "Прочитать позже" }) { Text("Позже · ${readLater.size}") }
                }
            }
            if (collections.isEmpty() && !creating) item { Text("Создайте подборку для сохранённых материалов.") }
            items(collections, key = { it.id }) { collection ->
                OutlinedCard(onClick = { selected = collection.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(collection.name, style = MaterialTheme.typography.titleMedium)
                        Text(collection.source ?: "Ручная коллекция")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionEditor(model: LibraryModel, sources: List<String>, onCreated: (Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingSource by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название коллекции") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Text(if (source == null) "Ручная коллекция" else "Умная коллекция по источнику")
        if (sources.isNotEmpty()) Box {
            OutlinedButton(onClick = { choosingSource = true }) { Text(source ?: "Выбрать источник") }
            DropdownMenu(expanded = choosingSource, onDismissRequest = { choosingSource = false }) {
                DropdownMenuItem(text = { Text("Ручная коллекция") }, onClick = {
                    source = null
                    choosingSource = false
                })
                sources.forEach { host ->
                    DropdownMenuItem(text = { Text(host) }, onClick = {
                        source = host
                        choosingSource = false
                    })
                }
            }
        }
        Button(enabled = name.isNotBlank(), onClick = {
            val chosen = source
            val title = name
            model.libraryUpdate {
                val result = if (chosen == null) createManualCollection(title) else createSourceCollection(title, chosen)
                onCreated(result.id)
            }
        }) { Text("Сохранить коллекцию") }
    }
}

@Composable
private fun CollectionContents(
    model: LibraryModel,
    entry: LibraryCollection,
    materials: List<Material>,
    modifier: Modifier,
    open: (Long) -> Unit,
    back: () -> Unit,
) {
    val members by remember(entry.id) { model.repository.collectionMaterials(entry.id) }.collectAsStateWithLifecycle(emptyList())
    var editing by rememberSaveable(entry.id) { mutableStateOf(false) }
    val ids = remember(members) { members.mapTo(mutableSetOf()) { it.id } }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(onClick = back) { Text("К коллекциям") } }
        item { Text(entry.name, style = MaterialTheme.typography.headlineSmall) }
        if (entry.kind == CollectionKind.MANUAL) item {
            OutlinedButton(onClick = { editing = !editing }) { Text(if (editing) "Готово" else "Редактировать состав") }
        }
        if (editing) items(materials, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, Modifier.weight(1f))
                Switch(checked = item.id in ids, onCheckedChange = { checked ->
                    model.libraryUpdate { setCollectionMembership(entry.id, item.id, checked) }
                })
            }
        } else {
            if (members.isEmpty()) item { Text("В коллекции пока нет материалов.") }
            items(members, key = { it.id }) { item -> OpenMaterialRow(item, open) }
        }
    }
}

@Composable
private fun OpenMaterialRow(material: Material, open: (Long) -> Unit) {
    OutlinedCard(onClick = { open(material.id) }, modifier = Modifier.fillMaxWidth()) {
        Text(material.title, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(14.dp))
    }
}

@Composable
internal fun ReadingControls(model: LibraryModel, materialId: Long) {
    val repo = model.repository
    val notes by remember(materialId) { repo.notes(materialId) }.collectAsStateWithLifecycle(emptyList())
    val later by remember(materialId) { repo.isReadLater(materialId) }.collectAsStateWithLifecycle(false)
    var draft by rememberSaveable(materialId) { mutableStateOf("") }
    var editing by rememberSaveable(materialId) { mutableStateOf<Long?>(null) }
    var revision by rememberSaveable(materialId) { mutableLongStateOf(0L) }
    var conflict by rememberSaveable(materialId) { mutableStateOf(false) }
    var writing by remember(materialId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { model.libraryUpdate { setReadLater(materialId, !later) } }) {
            Text(if (later) "Убрать из «Прочитать позже»" else "Прочитать позже")
        }
        Text("Заметки", style = MaterialTheme.typography.titleLarge)
        notes.forEach { note ->
            OutlinedCard(enabled = !writing, onClick = {
                editing = note.id
                revision = note.revision
                draft = note.body
                conflict = false
            }, modifier = Modifier.fillMaxWidth()) {
                Text(note.body, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(14.dp))
            }
        }
        OutlinedTextField(
            value = draft, onValueChange = { draft = it }, enabled = !writing,
            label = { Text(if (editing == null) "Новая заметка" else "Редактировать заметку") },
            modifier = Modifier.fillMaxWidth(), minLines = 2,
        )
        if (conflict) Text("Заметка изменилась. Сохраните свой текст отдельно или откройте текущую версию.",
            color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = draft.isNotBlank() && !conflict && !writing, onClick = {
                val input = draft
                val noteId = editing
                val expected = revision
                writing = true
                model.libraryUpdate(onFinish = { writing = false }) {
                    if (noteId == null) createNote(materialId, input)
                    else when (editNote(noteId, expected, input)) {
                        is NoteEditResult.Saved -> Unit
                        is NoteEditResult.Conflict, NoteEditResult.Missing -> {
                            conflict = true
                            return@libraryUpdate
                        }
                    }
                    editing = null
                    draft = ""
                }
            }) { Text("Сохранить заметку") }
            if (editing != null) TextButton(enabled = !writing, onClick = {
                editing = null
                conflict = false
            }) { Text("Как новую") }
        }
    }
}

@Composable
internal fun RelatedMaterials(model: LibraryModel, materialId: Long, open: (Long) -> Unit) {
    val related by remember(materialId) { model.repository.relatedMaterials(materialId) }.collectAsStateWithLifecycle(emptyList())
    if (related.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Связанные материалы", style = MaterialTheme.typography.titleLarge)
        related.forEach { material -> OpenMaterialRow(material, open) }
    }
}
