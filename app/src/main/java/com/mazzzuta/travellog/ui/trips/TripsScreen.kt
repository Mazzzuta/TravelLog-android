package com.mazzzuta.travellog.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.utils.*
import com.mazzzuta.travellog.viewmodels.TripsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun TripsScreen(onBack: () -> Unit, onEntryClick: (Long) -> Unit, viewModel: TripsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<TripEntity?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val dateFormat = LocalDateFormat.current
    BackHandler(enabled = state.isDeleting) { }
    LaunchedEffect(state.saved) {
        if (state.saved) { editing = null; viewModel.clearMessage() }
    }
    LaunchedEffect(state.error) {
        if (editing == null && state.tripToDelete == null) state.error?.let { snackbar.showSnackbar(it) }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.isDeleting) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                Text("Мои поездки", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    viewModel.clearMessage()
                    editing = TripEntity(title = "", startDate = fromDatePickerMillis(toDatePickerMillis(System.currentTimeMillis())))
                }, enabled = !state.isLoading && !state.isDeleting) { Text("Создать") }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.trips.isEmpty() -> Column {
                    Text(state.error!!)
                    TextButton(onClick = viewModel::load) { Text("Повторить") }
                }
                state.trips.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Пока нет поездок. Нажмите «Создать», чтобы добавить первую.")
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.trips, key = { it.id }) { trip ->
                        val entries = state.entries.filter { it.entry.tripId == trip.id }
                        val cover = trip.coverPhotoPath ?: entries.firstNotNullOfOrNull { it.photos.firstOrNull()?.filePath }
                        Card(onClick = { expandedId = if (expandedId == trip.id) null else trip.id }, shape = RoundedCornerShape(20.dp)) {
                            cover?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(150.dp)) }
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(trip.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${formatDate(trip.startDate, dateFormat)} — ${trip.endDate?.let { formatDate(it, dateFormat) } ?: "поездка продолжается"}",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("Записей: ${entries.size}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.clearMessage(); editing = trip }, enabled = !state.isDeleting) {
                                    Icon(Icons.Default.Edit, contentDescription = "Изменить поездку")
                                }
                                IconButton(onClick = { viewModel.requestDelete(trip) }, enabled = !state.isDeleting) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить поездку", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            OutlinedButton(
                                onClick = { expandedId = if (expandedId == trip.id) null else trip.id },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                            ) {
                                Text(if (expandedId == trip.id) "Скрыть записи" else "Посмотреть записи (${entries.size})")
                            }
                            if (expandedId == trip.id) {
                                if (entries.isEmpty()) Text(
                                    "В этой поездке пока нет записей. При добавлении или редактировании записи выберите эту поездку.",
                                    Modifier.padding(16.dp),
                                )
                                entries.forEach { entry ->
                                    HorizontalDivider()
                                    ListItem(
                                        modifier = Modifier.clickable { onEntryClick(entry.entry.id) },
                                        headlineContent = { Text(entry.entry.title) },
                                        supportingContent = {
                                            Column {
                                                Text(formatDate(entry.entry.date, dateFormat))
                                                entry.entry.placeName?.let { Text(it) }
                                                if (entry.entry.description.isNotBlank()) {
                                                    Text(entry.entry.description, maxLines = 2,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                }
                                            }
                                        },
                                        leadingContent = {
                                            entry.photos.minByOrNull { it.orderIndex }?.let { photo ->
                                                AsyncImage(model = photo.filePath, contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                            }
                                        },
                                        trailingContent = { Text("Нажмите,\nчтобы открыть", style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { trip ->
        TripEditor(trip, state.isSaving, state.error, { editing = null; viewModel.clearMessage() }, viewModel::save)
    }
    state.tripToDelete?.let { trip ->
        val count = state.entries.count { it.entry.tripId == trip.id }
        var deleteEntries by rememberSaveable(trip.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Удалить поездку?") },
            text = {
                Column {
                    Text(if (deleteEntries)
                        "Поездка «${trip.title}» и все её записи будут удалены. Это действие нельзя отменить."
                    else "Поездка «${trip.title}» будет удалена. Все её записи, фото и теги останутся в дневнике. Записи станут «Без поездки».")
                    if (count > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteEntries, onCheckedChange = { deleteEntries = it }, enabled = !state.isDeleting)
                        Text("Удалить также записи ($count)")
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(deleteEntries) }, enabled = !state.isDeleting) {
                    if (state.isDeleting) CircularProgressIndicator(Modifier.size(20.dp))
                    else Text(if (deleteEntries) "Удалить поездку и записи" else "Удалить только поездку", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete, enabled = !state.isDeleting) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripEditor(trip: TripEntity, isSaving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (TripEntity) -> Unit) {
    var title by rememberSaveable(trip.id) { mutableStateOf(trip.title) }
    var start by rememberSaveable(trip.id) { mutableStateOf(fromDatePickerMillis(toDatePickerMillis(trip.startDate))) }
    var end by rememberSaveable(trip.id) { mutableStateOf(trip.endDate?.let { fromDatePickerMillis(toDatePickerMillis(it)) }) }
    var choosingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val invalidTitle = title.isBlank()
    val invalidDates = end?.let { it < start } == true
    val dateFormat = LocalDateFormat.current
    BackHandler(enabled = isSaving) { }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (trip.id == 0L) "Новая поездка" else "Изменить поездку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название *") },
                    isError = attempted && invalidTitle, enabled = !isSaving, singleLine = true)
                if (attempted && invalidTitle) Text("Введите название поездки", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { choosingStart = true }, enabled = !isSaving) { Text("Начало: ${formatDate(start, dateFormat)}") }
                TextButton(onClick = { choosingStart = false }, enabled = !isSaving) {
                    Text("Окончание: ${end?.let { formatDate(it, dateFormat) } ?: "не указано"}")
                }
                if (end != null) TextButton(onClick = { end = null }, enabled = !isSaving) { Text("Поездка ещё продолжается") }
                if (invalidDates) Text("Дата окончания раньше начала", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = {
                attempted = true
                if (!invalidTitle && !invalidDates) onSave(trip.copy(title = title.trim(), startDate = start, endDate = end))
            }) { if (isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Отмена") } },
    )
    choosingStart?.let { isStart ->
        val picker = rememberDatePickerState(initialSelectedDateMillis = toDatePickerMillis(if (isStart) start else end ?: start))
        DatePickerDialog(onDismissRequest = { choosingStart = null }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { if (isStart) start = fromDatePickerMillis(it) else end = fromDatePickerMillis(it) }
                choosingStart = null
            }) { Text("Выбрать") }
        }, dismissButton = { TextButton(onClick = { choosingStart = null }) { Text("Отмена") } }) { DatePicker(state = picker) }
    }
}
