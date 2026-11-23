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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.knowledgehub.core.*
import kotlinx.coroutines.CancellationException
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
                    primary = Color(0xFF5148CC),
                    primaryContainer = Color(0xFFE7E3FF),
                    onPrimaryContainer = Color(0xFF261B74),
                    background = Color(0xFFF7F9FD),
                    onBackground = Color(0xFF182238),
                    surface = Color.White,
                    onSurface = Color(0xFF182238),
                    onSurfaceVariant = Color(0xFF52617A),
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

private enum class Destination(val title: String, val shortTitle: String, val icon: ImageVector) {
    Library("Библиотека", "Главная", Icons.Default.List),
    Collections("Коллекции", "Коллекции", Icons.Default.Star),
    Add("Добавить", "Добавить", Icons.Default.Add),
    Search("Поиск", "Поиск", Icons.Default.Search),
    Ai("AI", "AI", Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryApp(model: LibraryModel) {
    var tab by rememberSaveable { mutableStateOf(Destination.Library) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val pending by model.pending.collectAsStateWithLifecycle(emptyList())
    val busy by model.busy.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val all by model.repository.materials.collectAsStateWithLifecycle(initialValue = null)
    val results by remember(query) { model.repository.search(query) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(pending) { if (pending.isNotEmpty()) tab = Destination.Add }
    var handledSave by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(saved) {
        if (saved > handledSave) tab = Destination.Library
        handledSave = saved
    }
    LaunchedEffect(message) {
        message?.let { snackbars.showSnackbar(it); model.message.compareAndSet(it, null) }
    }
    val item = all?.find { it.id == selected }
    BackHandler(selected != null || tab != Destination.Library) {
        if (selected != null) selected = null else tab = Destination.Library
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) tab.title else "Материал") },
                navigationIcon = {
                    if (selected != null) IconButton(onClick = { selected = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (selected == null && tab == Destination.Library) {
                        IconButton(onClick = { tab = Destination.Search }) {
                            Icon(Icons.Default.Search, contentDescription = "Найти материал")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == tab,
                        onClick = { tab = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.shortTitle, maxLines = 1) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).imePadding()
        when {
            selected != null -> item?.let { Detail(it, body, model) }
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
                else Materials(results, "Ничего не найдено", "Попробуйте изменить запрос.", onOpen = { selected = it })
            }
            tab == Destination.Collections -> Materials(
                all?.filter { it.favorite }, "В избранном пока пусто",
                "Откройте материал и добавьте его в избранное.", body, { selected = it }, "Избранное",
            )
            tab == Destination.Ai -> Materials(
                all, "Нет сохранённых материалов", "Сохраните ссылку, текст или файл.", body,
                { selected = it }, "Материалы без AI-обработки", showProcessing = true,
            )
            else -> Materials(all, "Библиотека пока пуста",
                "Добавьте первый материал или отправьте его из меню «Поделиться».", body, { selected = it },
                emptyAction = { Button(onClick = { tab = Destination.Add }) { Text("Добавить материал") } },
            )
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
    showProcessing: Boolean = false,
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
                        if (showProcessing) Text("Сохранён локально")
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
private fun Detail(material: Material, modifier: Modifier, model: LibraryModel) {
    val context = LocalContext.current
    var error by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(material.title, style = MaterialTheme.typography.headlineSmall) }
        item { Text(if (material.duplicateOf == null) "Сохранён локально" else "Сохранён отдельно · возможный дубликат") }
        item {
            OutlinedButton(onClick = { model.favorite(material) }) {
                Text(if (material.favorite) "Убрать из избранного" else "В избранное")
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
