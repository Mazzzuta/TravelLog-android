package com.mazzzuta.travellog.ui.createentry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mazzzuta.travellog.viewmodels.CreateEntryViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import com.mazzzuta.travellog.database.TagEntity
import com.mazzzuta.travellog.database.MAX_TAG_LENGTH
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import com.mazzzuta.travellog.database.containsDate
import com.mazzzuta.travellog.utils.LocalDateFormat
import com.mazzzuta.travellog.utils.formatDate
import com.mazzzuta.travellog.utils.toDatePickerMillis
import com.mazzzuta.travellog.utils.fromDatePickerMillis

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CreateEntryScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    entryId: Long? = null,
    viewModel: CreateEntryViewModel = koinViewModel(parameters = { parametersOf(entryId) })
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var tagToDelete by remember { mutableStateOf<TagEntity?>(null) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val leave: () -> Unit = { if (state.hasChanges && !state.isSaved) showDiscardDialog = true else onCancel() }
    BackHandler { if (!state.isSaving) leave() }

    if (showDiscardDialog) {
        AlertDialog(onDismissRequest = { showDiscardDialog = false }, title = { Text("Выйти без сохранения?") },
            text = { Text("Изменения в записи будут потеряны.") },
            confirmButton = { TextButton(onClick = { showDiscardDialog = false; onCancel() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Остаться") } })
    }
    tagToDelete?.let { tag ->
        AlertDialog(onDismissRequest = { if (!state.isDeletingTag) tagToDelete = null },
            title = { Text("Удалить тег?") },
            text = { Column {
                Text("Тег «${tag.name}» будет удалён из всех записей. Сами записи останутся.")
                state.tagDeleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isDeletingTag) LinearProgressIndicator(Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = !state.isDeletingTag, onClick = {
                viewModel.deleteTag(tag.id) { tagToDelete = null }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(enabled = !state.isDeletingTag, onClick = { tagToDelete = null }) { Text("Отмена") } })
    }
    LaunchedEffect(state.formError) {
        state.formError?.let { snackbarHostState.showSnackbar(it); viewModel.onFormErrorShown() }
    }

    LaunchedEffect(state.saveError) {
        state.saveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSaveErrorShown()
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    LaunchedEffect(state.titleError) {
        state.titleError?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.duplicatePhotosSkipped) {
        if (state.duplicatePhotosSkipped > 0) {
            snackbarHostState.showSnackbar("Пропущено уже добавленных фото: ${state.duplicatePhotosSkipped}")
            viewModel.onDuplicateMessageShown()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* shortcut: источник не сохраняет доступ; после перезапуска потребуется повторный выбор фото. */ }
        }
        viewModel.onPhotosPicked(uris.map { it.toString() })
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.detectLocation() else viewModel.onLocationPermissionDenied() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = leave, enabled = !state.isSaving) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                Text(if (entryId == null) "Новая запись" else "Изменить запись", fontWeight = FontWeight.ExtraBold)
                Button(onClick = { viewModel.save() }, enabled = !state.isSaving && !state.isLoading && !state.isDetectingLocation && !state.isAddingTag && !state.isDeletingTag && state.loadError == null) {
                    Box(contentAlignment = Alignment.Center) {
                        // Прозрачный текст удерживает ширину кнопки, пока поверх него показывается индикатор.
                        Text("Сохранить", modifier = Modifier.alpha(if (state.isSaving) 0f else 1f))
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading || state.isSaving || state.loadError != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading || state.isSaving) CircularProgressIndicator()
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::loadEntry) { Text("Повторить") }
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
        ) {
            Text(
                "ФОТОГРАФИИ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.photoUris, key = { it }) { uri ->
                    Box(modifier = Modifier.size(96.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Добавлено",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .size(20.dp)
                                .background(Color.White, CircleShape)
                        )
                        IconButton(
                            onClick = { viewModel.onPhotoRemoved(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Убрать фото",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.size(96.dp),
                        contentPadding = PaddingValues(8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Text("Добавить", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                placeholder = { Text("Название записи *") },
                isError = state.titleError != null,
                supportingText = { state.titleError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                textStyle = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChanged,
                placeholder = { Text("Напиши что-нибудь об этом месте...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                minLines = 4
            )

            Spacer(Modifier.height(20.dp))

            var showDatePicker by rememberSaveable { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Дата: ${formatDate(state.date, LocalDateFormat.current)}", modifier = Modifier.weight(1f))
                Text("Изменить")
            }
            if (showDatePicker) {
                val selectableDates = remember {
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            utcTimeMillis <= toDatePickerMillis(System.currentTimeMillis())

                        override fun isSelectableYear(year: Int): Boolean = year <= java.time.Year.now().value
                    }
                }
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = toDatePickerMillis(state.date),
                    selectableDates = selectableDates,
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            enabled = datePickerState.selectedDateMillis?.let(selectableDates::isSelectableDate) == true,
                            onClick = {
                                datePickerState.selectedDateMillis?.let { viewModel.onDateChanged(fromDatePickerMillis(it)) }
                                showDatePicker = false
                            },
                        ) { Text("Выбрать") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
                    },
                ) { DatePicker(state = datePickerState) }
            }
            Spacer(Modifier.height(20.dp))

            var tripMenuExpanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                OutlinedButton(onClick = { tripMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Luggage, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(if (state.autoAssignTrip) "Автоопределение" else "Без поездки", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать способ привязки к поездке")
                }
                DropdownMenu(expanded = tripMenuExpanded, onDismissRequest = { tripMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Без поездки") }, onClick = { viewModel.onTripAssignmentChanged(false); tripMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Автоопределение") }, onClick = { viewModel.onTripAssignmentChanged(true); tripMenuExpanded = false })
                }
            }
            if (state.autoAssignTrip) {
                val matches = state.availableTrips.filter { it.containsDate(state.date) }
                Text(when (matches.size) {
                    0 -> "Поездка на эту дату не найдена. Запись сохранится без поездки."
                    1 -> "Поездка: «${matches.single().title}»"
                    else -> "На эту дату найдено несколько поездок. Исправьте пересечение их периодов."
                }, modifier = Modifier.padding(horizontal = 20.dp),
                    color = if (matches.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))

            Text("ТЕГИ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableTags.forEach { tag ->
                    val selected = tag.id in state.selectedTagIds
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(vertical = 4.dp).combinedClickable(
                            enabled = !state.isDeletingTag && !state.isAddingTag,
                            role = Role.Checkbox, onLongClickLabel = "Удалить тег",
                            onClick = { viewModel.toggleTag(tag.id) },
                            onLongClick = { viewModel.clearTagDeleteError(); tagToDelete = tag }
                        ).semantics {
                            this.selected = selected
                            toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                        }) {
                        Text(tag.name, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.newTagName,
                    onValueChange = viewModel::onNewTagNameChanged,
                    placeholder = { Text("Новый тег...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = { Text("${state.newTagName.length}/$MAX_TAG_LENGTH · Удерживайте тег для удаления") }
                )
                IconButton(onClick = { viewModel.addNewTag() }, enabled = state.newTagName.isNotBlank() && !state.isAddingTag && !state.isDeletingTag) {
                    if (state.isAddingTag) CircularProgressIndicator(Modifier.size(18.dp))
                    else Icon(Icons.Default.Add, contentDescription = "Добавить тег")
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.placeName ?: "Место не указано",
                    modifier = Modifier.weight(1f)
                )
                if (state.isDetectingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                }
            }

            if (state.latitude != null || state.placeName != null) {
                TextButton(onClick = viewModel::clearLocation, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Убрать место") }
            }
            state.locationSearchError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            }

            var showLocationSearch by rememberSaveable { mutableStateOf(false) }
            var searchQuery by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(state.locationFound) { if (state.locationFound) showLocationSearch = false }
            val dismissSearch: () -> Unit = { viewModel.cancelLocationSearch(); showLocationSearch = false }

            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (maxWidth < 340.dp) 1 else 2
                ) {
                    OutlinedButton(
                        onClick = {
                            val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) viewModel.detectLocation()
                            else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = !state.isDetectingLocation
                    ) {
                        Text("Автоматически")
                    }
                    OutlinedButton(
                        onClick = { viewModel.cancelLocationSearch(); showLocationSearch = true },
                        enabled = !state.isDetectingLocation,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Ввести вручную")
                    }
                }
            }

            if (showLocationSearch) {
                AlertDialog(
                    onDismissRequest = dismissSearch,
                    title = { Text("Введите место") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Например: Санторини, Греция") },
                                singleLine = true,
                                enabled = !state.isDetectingLocation
                            )
                            state.locationSearchError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.isDetectingLocation) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = searchQuery.isNotBlank() && !state.isDetectingLocation, onClick = {
                            viewModel.searchLocationByName(searchQuery)
                        }) { Text("Найти") }
                    },
                    dismissButton = {
                        TextButton(onClick = dismissSearch) { Text("Отмена") }
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
