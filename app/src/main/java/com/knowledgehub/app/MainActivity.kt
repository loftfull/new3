package com.knowledgehub.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.knowledgehub.app.processing.enqueueProcessing
import com.knowledgehub.core.*
import com.knowledgehub.core.processing.ProcessingRecord
import com.knowledgehub.core.processing.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: LibraryModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.WHITE),
        )
        model.receiveOnce(intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = LibraryPalette.Accent,
                    primaryContainer = LibraryPalette.Violet,
                    onPrimaryContainer = Color(0xFF261B74),
                    background = LibraryPalette.Background,
                    onBackground = LibraryPalette.Ink,
                    surface = Color.White,
                    onSurface = LibraryPalette.Ink,
                    onSurfaceVariant = LibraryPalette.Muted,
                ),
            ) { LibraryApp(model) }
        }
    }
}

class LibraryModel(application: Application, private val state: SavedStateHandle) : AndroidViewModel(application) {
    val repository = CaptureRepository.shared(application)
    val pending = state.getStateFlow("pending", arrayListOf<Bundle>()).map { entries -> entries.map(Bundle::captureInput) }
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(0L)

    init {
        startProcessing()
    }

    private fun startProcessing() {
        runCatching { enqueueProcessing(getApplication()) }
            .onFailure { message.value = "Фоновая обработка недоступна. Материалы остаются в библиотеке." }
    }

    fun libraryUpdate(onFinish: () -> Unit = {}, update: suspend CaptureRepository.() -> Unit) {
        viewModelScope.launch {
            try {
                repository.update()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message.value = "Не удалось изменить данные. Попробуйте ещё раз."
            } finally {
                onFinish()
            }
        }
    }

    fun exportLibrary(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val output = checkNotNull(resolver.openOutputStream(uri, "wt"))
                    output.bufferedWriter(Charsets.UTF_8).use { repository.exportLibrary(it) }
                }
                message.value = "Экспорт завершён."
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message.value = "Не удалось завершить экспорт. Попробуйте другое место."
            }
        }
    }

    fun retryProcessing(id: Long) {
        viewModelScope.launch {
            try {
                if (repository.retryProcessing(id)) startProcessing()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message.value = "Не удалось повторить обработку. Материал сохранён."
            }
        }
    }

    fun preview(inputs: List<CaptureInput>) {
        state["pending"] = ArrayList(inputs.map(CaptureInput::bundle))
    }

    fun receiveOnce(intent: Intent) {
        if (state.get<Boolean>("shareReceived") == true) return
        state["shareReceived"] = true
        runCatching { ShareCapture.fromIntent(intent) }
            .onSuccess { inputs ->
                preview(inputs)
                if (inputs.isEmpty() && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)) {
                    message.value = "В отправленных данных нет материалов для сохранения."
                }
            }
            .onFailure { message.value = "Не удалось прочитать данные из приложения." }
    }

    fun save(inputs: List<CaptureInput>) {
        if (inputs.isEmpty() || !busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                repository.saveAll(inputs)
                preview(emptyList())
                saved.value += 1
                startProcessing()
                message.value = if (inputs.size == 1) "Материал сохранён" else "Материалов сохранено: ${inputs.size}"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = "Не удалось сохранить материал. Проверьте ссылку, доступ к файлу и свободное место."
            } finally {
                busy.value = false
            }
        }
    }

    fun favorite(material: Material) {
        viewModelScope.launch {
            try {
                repository.setFavorite(material.id, !material.favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = "Не удалось обновить избранное."
            }
        }
    }
}

private enum class Destination(val title: String, val icon: ImageVector) {
    Library("Библиотека", Icons.AutoMirrored.Filled.List),
    Collections("Коллекции", Icons.Default.Star),
    Add("Добавить", Icons.Default.Add),
    Search("Поиск", Icons.Default.Search),
    Ai("AI", Icons.Default.Info),
}

private enum class LibraryPage(val title: String) {
    Recent("Библиотека"), All("Все материалы"), Profile("Профиль и настройки"), Analytics("Аналитика"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryApp(model: LibraryModel) {
    var tab by rememberSaveable { mutableStateOf(Destination.Library) }
    var libraryPage by rememberSaveable { mutableStateOf(LibraryPage.Recent) }
    var libraryFilter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    val areaState = rememberSaveableStateHolder()
    val recentScroll = rememberLazyListState()
    val allScroll = rememberLazyListState()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var materialPath by rememberSaveable { mutableStateOf(arrayListOf<Long>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val pending by model.pending.collectAsStateWithLifecycle(emptyList())
    val busy by model.busy.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val all by model.repository.materials.collectAsStateWithLifecycle(initialValue = null)
    val processing by model.repository.processingRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(pending) { if (pending.isNotEmpty()) tab = Destination.Add }
    var handledSave by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(saved) {
        if (saved > handledSave) {
            tab = Destination.Library
            libraryPage = LibraryPage.Recent
            libraryFilter = LibraryFilter.All
            recentScroll.scrollToItem(0)
        }
        handledSave = saved
    }
    LaunchedEffect(message) {
        message?.let { snackbars.showSnackbar(it); model.message.compareAndSet(it, null) }
    }
    val item = all?.find { it.id == selected }
    fun openMaterial(id: Long) {
        selected = id
        model.libraryUpdate { recordReading(id) }
    }
    fun openRelated(id: Long) {
        selected?.let { materialPath = ArrayList(materialPath + it) }
        openMaterial(id)
    }
    fun goBack() {
        when {
            selected != null -> {
                selected = materialPath.lastOrNull()
                materialPath = ArrayList(materialPath.dropLast(1))
            }
            tab != Destination.Library -> tab = Destination.Library
            libraryPage == LibraryPage.Analytics -> libraryPage = LibraryPage.Profile
            else -> libraryPage = LibraryPage.Recent
        }
    }
    val canGoBack = selected != null || tab != Destination.Library || libraryPage != LibraryPage.Recent
    val libraryHome = selected == null && tab == Destination.Library && libraryPage == LibraryPage.Recent
    val child = selected != null || (tab == Destination.Library && libraryPage != LibraryPage.Recent)
    BackHandler(canGoBack, onBack = ::goBack)
    Scaffold(
        topBar = {
            if (libraryHome) {
                LibraryHeader(all?.size) { libraryPage = LibraryPage.Profile }
            } else {
                TopAppBar(
                    title = {
                        Text(when {
                            selected != null -> "Материал"
                            tab == Destination.Library -> libraryPage.title
                            else -> tab.title
                        })
                    },
                    navigationIcon = {
                        if (child) IconButton(onClick = ::goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (!child) ReferenceNavigation(tab) { destination ->
                tab = destination
                if (destination == Destination.Library) libraryPage = LibraryPage.Recent
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
        when {
            selected != null -> item?.let { material ->
                areaState.SaveableStateProvider("material-${material.id}") {
                    Detail(material, body, model, processing.firstOrNull { it.materialId == material.id }, ::openRelated)
                }
            }
            tab == Destination.Add -> CaptureForm(model, pending, busy, body)
            tab == Destination.Search -> Column(body) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Поиск по библиотеке") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (query.isBlank()) Placeholder("Введите запрос", "Поиск работает по сохранённым материалам.")
                else key(query) {
                    val results by remember(query) { model.repository.search(query) }
                        .collectAsStateWithLifecycle(initialValue = null)
                    Materials(results, "Ничего не найдено", "Попробуйте изменить запрос.", onOpen = ::openMaterial)
                }
            }
            tab == Destination.Collections -> areaState.SaveableStateProvider("collections") {
                CollectionLibrary(model, all.orEmpty(), body, ::openMaterial)
            }
            tab == Destination.Ai -> ProcessingQueue(all, processing, body, ::openMaterial, model::retryProcessing)
            libraryPage == LibraryPage.Profile -> LocalProfile(all?.size, body, model::exportLibrary) {
                libraryPage = LibraryPage.Analytics
            }
            libraryPage == LibraryPage.Analytics -> LocalAnalytics(all, processing, body)
            else -> LibraryList(
                entries = all,
                filter = libraryFilter,
                onFilter = { libraryFilter = it },
                allMaterials = libraryPage == LibraryPage.All,
                oldestFirst = oldestFirst,
                onChangeSort = { oldestFirst = !oldestFirst },
                scrollState = if (libraryPage == LibraryPage.All) allScroll else recentScroll,
                onShowAll = { libraryPage = LibraryPage.All },
                onSearch = { tab = Destination.Search },
                onAdd = { tab = Destination.Add },
                onOpen = ::openMaterial,
                modifier = body,
            )
        }
    }
}

@Composable
private fun ReferenceNavigation(selected: Destination, onSelect: (Destination) -> Unit) {
    Surface(color = Color.White) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = LibraryPalette.Divider)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp).selectableGroup()) {
                Destination.entries.forEach { destination ->
                    val active = destination == selected
                    val accentButton = destination == Destination.Add
                    Column(
                        Modifier.weight(1f).heightIn(min = 59.dp)
                            .selectable(active, role = Role.Tab, onClick = { onSelect(destination) })
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        val chip = when {
                            accentButton -> LibraryPalette.Accent
                            active -> LibraryPalette.Violet
                            else -> Color.Transparent
                        }
                        Box(
                            Modifier.size(47.dp, 28.dp).background(chip, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                destination.icon, contentDescription = null, modifier = Modifier.size(21.dp),
                                tint = when {
                                    accentButton -> Color.White
                                    active -> LibraryPalette.Accent
                                    else -> LibraryPalette.Muted
                                },
                            )
                        }
                        Text(
                            destination.title, textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 13.sp,
                            color = if (active) LibraryPalette.Accent else LibraryPalette.Muted,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Materials(
    list: List<Material>?,
    emptyTitle: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    onOpen: (Long) -> Unit,
    heading: String? = null,
    emptyAction: @Composable (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (heading != null) item { Text(heading, style = MaterialTheme.typography.titleLarge) }
        when {
            list == null -> item { CircularProgressIndicator() }
            list.isEmpty() -> item { Placeholder(emptyTitle, emptyText, emptyAction) }
            else -> items(list, key = { it.id }) { material ->
                OutlinedCard(onClick = { onOpen(material.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(material.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                        val subtitle = material.source ?: material.fileName
                        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (material.duplicateOf != null) Text("Возможный дубликат")
                    }
                }
            }
        }
    }
}

@Composable
private fun Placeholder(title: String, text: String, action: @Composable (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun CaptureForm(
    model: LibraryModel,
    pending: List<CaptureInput>,
    busy: Boolean,
    modifier: Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.preview(uris.map { CaptureInput.File(it) })
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pending.isNotEmpty()) {
            item { Text("Проверьте перед сохранением", style = MaterialTheme.typography.titleLarge) }
            items(pending) { capture ->
                Text(
                    when (capture) {
                        is CaptureInput.Link -> capture.value
                        is CaptureInput.Text -> capture.value
                        is CaptureInput.File -> capture.uri.lastPathSegment ?: "Файл"
                    },
                    maxLines = 4,
                )
            }
            item {
                Button(enabled = !busy, onClick = { model.save(pending) }) { Text("Сохранить") }
                TextButton(enabled = !busy, onClick = { model.preview(emptyList()) }) { Text("Отмена") }
            }
        } else {
            item {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Ссылка или текст") },
                )
            }
            item {
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    model.preview(listOf(CaptureInput.Text(input)))
                    input = ""
                }) { Text("Продолжить") }
            }
            item { OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("Выбрать файлы") } }
        }
        if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Detail(
    material: Material, modifier: Modifier, model: LibraryModel, analysis: ProcessingRecord?, onRelated: (Long) -> Unit,
) {
    val context = LocalContext.current
    var error by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.testTag("material-detail"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(material.title, style = MaterialTheme.typography.headlineSmall) }
        item { Text(if (material.duplicateOf == null) "Сохранён локально" else "Сохранён отдельно · возможный дубликат") }
        item {
            OutlinedButton(onClick = { model.favorite(material) }) {
                Text(if (material.favorite) "Убрать из избранного" else "В избранное")
            }
        }
        if (analysis?.state == ProcessingState.COMPLETE) {
            item {
                Text("Предложенная тема · ${analysis.suggestedTopic}")
                analysis.summary?.let { Text(it) }
                if (analysis.keywords.isNotEmpty()) Text(analysis.keywords.joinToString(" · "))
            }
        }
        material.originalUrl?.let { url ->
            item {
                Text(url)
                Button(onClick = {
                    error = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))) }.isFailure
                }) { Text("Открыть ссылку") }
            }
        }
        material.originalText?.takeUnless { it == material.originalUrl }?.let { text -> item { Text(text) } }
        material.fileName?.let { file ->
            item {
                Text(file)
                Button(onClick = {
                    error = runCatching {
                        val attachment = requireNotNull(model.repository.attachment(material))
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", attachment, file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, material.mimeType ?: "application/octet-stream")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }.isFailure
                }) { Text("Открыть файл") }
            }
        }
        if (error) item { Text("Не удалось открыть материал.", color = MaterialTheme.colorScheme.error) }
        item { ReadingControls(model, material.id) }
        item { RelatedMaterials(model, material.id, onRelated) }
    }
}

private fun CaptureInput.bundle() = Bundle().apply {
    when (val input = this@bundle) {
        is CaptureInput.Link -> {
            putString("kind", "link")
            putString("value", input.value)
            putString("subject", input.subject)
        }
        is CaptureInput.Text -> {
            putString("kind", "text")
            putString("value", input.value)
            putString("subject", input.subject)
        }
        is CaptureInput.File -> {
            putString("kind", "file")
            putString("value", input.uri.toString())
            putString("mime", input.mimeType)
        }
    }
}

private fun Bundle.captureInput(): CaptureInput {
    val value = requireNotNull(getString("value"))
    return when (getString("kind")) {
        "link" -> CaptureInput.Link(value, getString("subject"))
        "file" -> CaptureInput.File(Uri.parse(value), getString("mime"))
        else -> CaptureInput.Text(value, getString("subject"))
    }
}

@Composable
private fun ProcessingQueue(
    materials: List<Material>?,
    records: List<ProcessingRecord>,
    modifier: Modifier,
    open: (Long) -> Unit,
    retry: (Long) -> Unit,
) {
    val states = remember(records) { records.associateBy { it.materialId } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Локальная обработка", style = MaterialTheme.typography.titleLarge) }
        when {
            materials == null -> item { CircularProgressIndicator() }
            materials.isEmpty() -> item { Placeholder("Нет сохранённых материалов", "Добавьте ссылку, текст или файл.") }
            else -> items(materials, key = { it.id }) { material ->
                val state = states[material.id]?.state ?: ProcessingState.QUEUED
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(material.title, style = MaterialTheme.typography.titleMedium)
                        Text(when (state) {
                            ProcessingState.QUEUED -> "В очереди"
                            ProcessingState.RUNNING -> "Обрабатывается"
                            ProcessingState.COMPLETE -> "Обработан локально"
                            ProcessingState.FAILED -> "Ошибка обработки · оригинал сохранён"
                        })
                        Row {
                            TextButton(onClick = { open(material.id) }) { Text("Открыть материал") }
                            if (state == ProcessingState.FAILED) {
                                TextButton(onClick = { retry(material.id) }) { Text("Повторить") }
                            }
                        }
                    }
                }
            }
        }
    }
}
