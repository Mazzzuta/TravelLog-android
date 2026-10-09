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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import com.mazzzuta.travellog.utils.LocalDateFormat
import com.mazzzuta.travellog.utils.formatDate
import com.mazzzuta.travellog.utils.toDatePickerMillis
import com.mazzzuta.travellog.utils.fromDatePickerMillis

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    BackHandler(enabled = state.isSaving) { }

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
    ) { uris -> viewModel.onPhotosPicked(uris.map { it.toString() }) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.detectLocation() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel, enabled = !state.isSaving) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                Text(if (entryId == null) "Новая запись" else "Изменить запись", fontWeight = FontWeight.ExtraBold)
                Button(onClick = { viewModel.save() }, enabled = !state.isSaving && !state.isLoading && !state.isDetectingLocation && state.loadError == null) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Сохранить")
                }
            }
        }
    ) { padding ->
        if (state.isLoading || state.isSaving || state.loadError != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading || state.isSaving) CircularProgressIndicator()
                else Text(state.loadError!!, color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // Фотографии
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
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Text("Добавить", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Название с валидацией
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                placeholder = { Text("Название записи *") },
                isError = state.titleError != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                textStyle = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            // Описание
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
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toDatePickerMillis(state.date))
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            enabled = datePickerState.selectedDateMillis != null,
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

            // Теги
            var tripMenuExpanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                OutlinedButton(onClick = { tripMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Luggage, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(state.availableTrips.firstOrNull { it.id == state.tripId }?.title ?: "Мои путешествия (по умолчанию)", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать поездку")
                }
                DropdownMenu(expanded = tripMenuExpanded, onDismissRequest = { tripMenuExpanded = false }) {
                    if (state.tripId == null) DropdownMenuItem(text = { Text("Мои путешествия (по умолчанию)") }, onClick = { tripMenuExpanded = false })
                    state.availableTrips.forEach { trip ->
                        DropdownMenuItem(text = { Text(trip.title) }, onClick = { viewModel.onTripSelected(trip.id); tripMenuExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Text("ТЕГИ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in state.selectedTagIds,
                        onClick = { viewModel.toggleTag(tag.id) },
                        label = { Text(tag.name) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.newTagName,
                    onValueChange = viewModel::onNewTagNameChanged,
                    placeholder = { Text("Новый тег...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { viewModel.addNewTag() }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить тег")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Локация
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

            var showLocationSearch by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.detectLocation()
                        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isDetectingLocation
                ) {
                    Text("Автоматически")
                }
                OutlinedButton(
                    onClick = { showLocationSearch = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ввести вручную")
                }
            }

            if (showLocationSearch) {
                AlertDialog(
                    onDismissRequest = { showLocationSearch = false },
                    title = { Text("Введите место") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Например: Санторини, Греция") },
                                singleLine = true
                            )
                            state.locationSearchError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.searchLocationByName(searchQuery)
                            showLocationSearch = false
                        }) { Text("Найти") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocationSearch = false }) { Text("Отмена") }
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
