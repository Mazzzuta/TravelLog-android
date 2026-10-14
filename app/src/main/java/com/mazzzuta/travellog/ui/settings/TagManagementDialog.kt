package com.mazzzuta.travellog.ui.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.viewmodels.SettingsViewModel

@Composable
internal fun TagManagementDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var selected by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.tags) { selected = selected.filter { id -> state.tags.any { it.id == id } } }
    AlertDialog(onDismissRequest = { if (!state.isDeletingTags) onDismiss() },
        title = { Text("Управление тегами") },
        text = { Column {
            Text("Выберите теги для удаления. Они будут удалены из всех записей, сами записи сохранятся.")
            if (state.isLoadingTags || state.isDeletingTags) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.tagsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (!confirmDelete) TextButton(onClick = viewModel::loadTags) { Text("Повторить загрузку") }
            }
            if (!state.isLoadingTags && state.tags.isEmpty()) Text("Пока нет тегов")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(state.tags, key = { it.id }) { tag ->
                    Row(Modifier.fillMaxWidth().toggleable(value = tag.id in selected,
                        enabled = !state.isDeletingTags, role = Role.Checkbox,
                        onValueChange = { checked -> selected = if (checked) selected + tag.id else selected - tag.id }
                    ).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tag.id in selected, onCheckedChange = null)
                        Text(tag.name, modifier = Modifier.weight(1f).padding(start = 8.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } },
        confirmButton = { TextButton(enabled = selected.isNotEmpty() && !state.isDeletingTags, onClick = { confirmDelete = true }) {
            Text("Удалить выбранные (${selected.size})")
        } },
        dismissButton = { TextButton(enabled = !state.isDeletingTags, onClick = onDismiss) { Text("Закрыть") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!state.isDeletingTags) confirmDelete = false },
        title = { Text("Удалить выбранные теги?") },
        text = { Column {
            Text("Тегов: ${selected.size}. Они исчезнут из всех записей. Отменить удаление нельзя.")
            state.tagsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !state.isDeletingTags && selected.isNotEmpty(), onClick = {
            viewModel.deleteTags(selected) { selected = emptyList(); confirmDelete = false }
        }) { Text("Удалить") } },
        dismissButton = { TextButton(enabled = !state.isDeletingTags, onClick = { confirmDelete = false }) { Text("Отмена") } })
}
