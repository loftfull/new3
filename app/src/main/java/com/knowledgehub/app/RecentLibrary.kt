package com.knowledgehub.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowledgehub.core.Material

internal object LibraryPalette {
    val Background = Color(0xFFF7F9FD)
    val Ink = Color(0xFF182238)
    val Muted = Color(0xFF52617A)
    val Accent = Color(0xFF5047C9)
    val Violet = Color(0xFFEEEBFF)
    val Blue = Color(0xFFEAF4FF)
    val Divider = Color(0xFFDDE5F0)
}

internal enum class LibraryFilter(val label: String) {
    All("Все"), Articles("Статьи"), Videos("Видео"), Pdf("PDF");

    fun includes(material: Material): Boolean = when (this) {
        All -> true
        Articles -> material.kind == "LINK" && !Videos.includes(material) && !Pdf.includes(material)
        Videos -> material.mimeType?.startsWith("video/", ignoreCase = true) == true ||
            material.source?.let { host ->
                host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
            } == true
        Pdf -> material.mimeType.equals("application/pdf", ignoreCase = true) ||
            material.fileName?.endsWith(".pdf", ignoreCase = true) == true ||
            material.originalUrl?.let { Uri.parse(it.trim()).path?.endsWith(".pdf", ignoreCase = true) } == true
    }
}

@Composable
internal fun LibraryHeader(count: Int?, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(LibraryPalette.Background)
            .statusBarsPadding().padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "Библиотека", Modifier.semantics { heading() }, fontSize = 29.sp,
                lineHeight = 34.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold,
            )
            if (count != null) {
                Text(pluralStringResource(R.plurals.library_material_count, count, count),
                    fontSize = 13.sp, color = LibraryPalette.Muted)
            }
        }
        Surface(
            onClick = onSettings, modifier = Modifier.size(48.dp), shape = CircleShape,
            color = Color.White, border = BorderStroke(1.dp, LibraryPalette.Divider),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_reference_profile), contentDescription = "Настройки профиля", modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
internal fun LibraryList(
    entries: List<Material>?,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    allMaterials: Boolean,
    oldestFirst: Boolean,
    onChangeSort: () -> Unit,
    scrollState: LazyListState,
    onShowAll: () -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(entries, filter, allMaterials, oldestFirst) {
        entries?.filter(filter::includes)?.let { matches ->
            when {
                !allMaterials -> matches.take(6)
                oldestFirst -> matches.asReversed()
                else -> matches
            }
        }
    }
    LazyColumn(
        modifier = modifier, state = scrollState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 22.dp),
    ) {
        item {
            Surface(
                onClick = onSearch, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), color = Color.White,
                border = BorderStroke(1.dp, LibraryPalette.Divider),
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_reference_search), contentDescription = null, tint = LibraryPalette.Muted,
                        modifier = Modifier.size(21.dp))
                    Text("Поиск по библиотеке", fontSize = 14.sp, color = LibraryPalette.Muted)
                }
            }
        }
        item {
            if (allMaterials) {
                TextButton(
                    onClick = onChangeSort,
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                ) { Text(if (oldestFirst) "Сначала старые" else "Сначала новые") }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Недавние", Modifier.weight(1f).semantics { heading() },
                        fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onShowAll, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Все материалы", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { LibraryFilters(filter, onFilter) }
        when {
            filtered == null -> item { CircularProgressIndicator(Modifier.padding(vertical = 12.dp)) }
            filtered.isEmpty() -> item {
                val emptyLibrary = entries?.isEmpty() == true
                Surface(
                    shape = RoundedCornerShape(16.dp), color = Color.White,
                    border = BorderStroke(1.dp, LibraryPalette.Divider),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (emptyLibrary) "Библиотека пока пуста" else "Нет материалов этого типа",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (emptyLibrary) {
                            Text("Добавьте материал или отправьте его из меню «Поделиться».",
                                style = MaterialTheme.typography.bodyMedium, color = LibraryPalette.Muted)
                            Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Добавить материал")
                            }
                        } else {
                            TextButton(
                                onClick = { onFilter(LibraryFilter.All) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Сбросить фильтр") }
                        }
                    }
                }
            }
            else -> itemsIndexed(filtered, key = { _, material -> material.id }) { index, material ->
                MaterialRow(material, { onOpen(material.id) })
                if (index < filtered.lastIndex) HorizontalDivider(color = LibraryPalette.Divider)
            }
        }
    }
}

@Composable
private fun LibraryFilters(selected: LibraryFilter, onFilter: (LibraryFilter) -> Unit) {
    Row(
        Modifier.padding(top = 3.dp, bottom = 7.dp).horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LibraryFilter.entries.forEach { filter ->
            val active = selected == filter
            val shape = RoundedCornerShape(22.dp)
            Surface(
                shape = shape,
                color = if (active) LibraryPalette.Violet else Color.White,
                border = if (active) null else BorderStroke(1.dp, LibraryPalette.Divider),
            ) {
                Box(
                    Modifier.clip(shape).selectable(active, role = Role.RadioButton, onClick = { onFilter(filter) })
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(filter.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (active) LibraryPalette.Accent else LibraryPalette.Ink)
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onOpen: () -> Unit) {
    val video = LibraryFilter.Videos.includes(material)
    val pdf = LibraryFilter.Pdf.includes(material)
    val tone = when {
        video || material.kind == "TEXT" -> LibraryPalette.Blue
        material.kind == "FILE" || pdf -> Color(0xFFEDF1F7)
        else -> LibraryPalette.Violet
    }
    val type = when {
        video -> "Видео"
        pdf -> "PDF"
        material.kind == "LINK" -> "Ссылка"
        material.kind == "TEXT" -> "Заметка"
        else -> "Файл"
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(tone, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(when {
                    video -> R.drawable.ic_reference_video
                    pdf || material.kind == "FILE" -> R.drawable.ic_reference_file
                    material.kind == "TEXT" -> R.drawable.ic_reference_note
                    else -> R.drawable.ic_reference_link
                }),
                contentDescription = null, modifier = Modifier.size(21.dp), tint = LibraryPalette.Accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(material.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(type, material.source, "Возможный дубликат".takeIf { material.duplicateOf != null })
                    .joinToString(" · "),
                fontSize = 12.sp, lineHeight = 17.sp, color = LibraryPalette.Muted,
            )
        }
        Icon(painterResource(R.drawable.ic_reference_chevron), contentDescription = null, tint = LibraryPalette.Muted,
            modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun LocalProfile(count: Int?, modifier: Modifier, export: (Uri) -> Unit, analytics: () -> Unit) {
    val context = LocalContext.current
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(export)
    }
    var launchError by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Локальное хранилище", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        }
        item { Text("Материалы хранятся на этом устройстве и доступны без сети.") }
        if (count != null) item { Text(pluralStringResource(R.plurals.library_material_count, count, count)) }
        item { OutlinedButton(onClick = analytics) { Text("Аналитика библиотеки") } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { createDocument.launch("knowledge-hub-library.json") }) {
                    Text("Экспортировать материалы и заметки")
                }
                Text("JSON содержит ссылки, текст и заметки. Файлы вложений сохраняются в библиотеке.")
            }
        }
        item {
            OutlinedButton(onClick = {
                launchError = runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }.isFailure
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Системные настройки приложения")
            }
        }
        if (launchError) item {
            Text("Не удалось открыть системные настройки.", color = MaterialTheme.colorScheme.error)
        }
    }
}
