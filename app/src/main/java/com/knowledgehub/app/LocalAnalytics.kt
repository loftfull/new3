package com.knowledgehub.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.knowledgehub.core.Material
import com.knowledgehub.core.processing.ProcessingRecord
import com.knowledgehub.core.processing.ProcessingState

@Composable
internal fun LocalAnalytics(materials: List<Material>?, processing: List<ProcessingRecord>, modifier: Modifier) {
    val weekStart = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val sources = remember(materials) {
        materials.orEmpty().groupingBy { it.source ?: "Локальные материалы" }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            materials == null -> item { CircularProgressIndicator() }
            materials.isEmpty() -> item { Text("Статистика появится после первого сохранения.") }
            else -> {
                item {
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Материалы · ${materials.size}", style = MaterialTheme.typography.titleLarge)
                            Text("За последние 7 дней · ${materials.count { it.createdAt >= weekStart }}")
                            Text("Избранное · ${materials.count { it.favorite }}")
                            Text("Обработано локально · ${processing.count { it.state == ProcessingState.COMPLETE }}")
                        }
                    }
                }
                item { Text("Источники", style = MaterialTheme.typography.titleLarge) }
                items(sources, key = { it.key }) { (source, count) ->
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(source)
                            Text("Материалов · $count")
                        }
                    }
                }
            }
        }
    }
}
