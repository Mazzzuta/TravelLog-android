package com.mazzzuta.travellog.ui.feed

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.database.EntryWithDetails
import com.mazzzuta.travellog.viewmodels.FeedViewModel
import com.mazzzuta.travellog.viewmodels.SortOption
import org.koin.androidx.compose.koinViewModel
import com.mazzzuta.travellog.utils.LocalDateFormat
import com.mazzzuta.travellog.utils.formatDate
import com.mazzzuta.travellog.utils.toDatePickerMillis
import com.mazzzuta.travellog.utils.fromDatePickerMillis

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onEntryClick: (Long) -> Unit,
    onTripsClick: () -> Unit,
    onCreateClick: () -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDateRange by rememberSaveable { mutableStateOf(false) }
    val dateFrom = state.dateFrom
    val dateTo = state.dateTo
    val hasFilters = state.tripId != null || state.onlyWithoutTrip || state.dateFrom != null || state.query.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Мои записи", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold)
                FilledTonalButton(onClick = onTripsClick, colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    Icon(Icons.Default.Luggage, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Поездки")
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск по названию или тексту") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var filtersExpanded by remember { mutableStateOf(false) }
                val activeFilters = listOf(state.tripId != null || state.onlyWithoutTrip, dateFrom != null).count { it }
                Box(Modifier.weight(1f)) {
                    FilterChip(selected = activeFilters > 0, onClick = { filtersExpanded = true },
                        label = { Text(if (activeFilters > 0) "Фильтры · $activeFilters" else "Фильтры") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) })
                    DropdownMenu(expanded = filtersExpanded, onDismissRequest = { filtersExpanded = false },
                        modifier = Modifier.widthIn(max = 300.dp).heightIn(max = 400.dp)) {
                        DropdownMenuItem(text = { Text("Поездка") }, enabled = false, onClick = {})
                        DropdownMenuItem(text = { Text("Все записи") },
                            trailingIcon = { if (state.tripId == null && !state.onlyWithoutTrip) Icon(Icons.Default.Check, null) },
                            onClick = { viewModel.onTripFilterChanged(null); filtersExpanded = false })
                        DropdownMenuItem(text = { Text("Без поездки") },
                            trailingIcon = { if (state.onlyWithoutTrip) Icon(Icons.Default.Check, null) },
                            onClick = { viewModel.onTripFilterChanged(null, true); filtersExpanded = false })
                        state.trips.forEach { trip ->
                            DropdownMenuItem(text = { Text(trip.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { if (state.tripId == trip.id) Icon(Icons.Default.Check, null) },
                                onClick = { viewModel.onTripFilterChanged(trip.id); filtersExpanded = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(if (dateFrom != null && dateTo != null)
                            "Период: ${formatDate(dateFrom, LocalDateFormat.current)} — ${formatDate(dateTo, LocalDateFormat.current)}" else "Выбрать период") },
                            onClick = { filtersExpanded = false; showDateRange = true })
                        if (hasFilters) DropdownMenuItem(text = { Text("Сбросить фильтры") },
                            onClick = { viewModel.clearFilters(); filtersExpanded = false })
                    }
                }
                var sortExpanded by remember { mutableStateOf(false) }
                Box(Modifier.width(180.dp), contentAlignment = Alignment.CenterEnd) {
                    AssistChip(modifier = Modifier.fillMaxWidth(), onClick = { sortExpanded = true },
                        label = { Text(state.sortOption.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) })
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                viewModel.onSortChanged(option); sortExpanded = false
                            })
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error!!) }
        } else if (state.entries.isEmpty()) {
            EmptyFeedState(hasFilters, if (hasFilters) viewModel::clearFilters else onCreateClick)
        } else {
            key(state.sortOption) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.entries, key = { it.entry.id }) { entryWithDetails ->
                        EntryCard(entryWithDetails = entryWithDetails, onClick = { onEntryClick(entryWithDetails.entry.id) })
                    }
                }
            }
        }
    }
    if (showDateRange) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.dateFrom?.let { toDatePickerMillis(it) },
            initialSelectedEndDateMillis = state.dateTo?.let { toDatePickerMillis(it) },
        )
        DatePickerDialog(onDismissRequest = { showDateRange = false }, confirmButton = {
            TextButton(enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null, onClick = {
                val start = picker.selectedStartDateMillis
                val end = picker.selectedEndDateMillis
                if (start != null && end != null) viewModel.onDateRangeChanged(fromDatePickerMillis(start), fromDatePickerMillis(end))
                showDateRange = false
            }) { Text("Применить") }
        }, dismissButton = {
            TextButton(onClick = { showDateRange = false }) { Text("Отмена") }
        }) { DateRangePicker(state = picker, modifier = Modifier.heightIn(max = 500.dp)) }
    }
}

@Composable
internal fun EntryCard(entryWithDetails: EntryWithDetails, onClick: () -> Unit) {
    val entry = entryWithDetails.entry
    val firstPhoto = entryWithDetails.photos.firstOrNull()?.filePath
    val textColor = if (firstPhoto != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (firstPhoto != null) {
                AsyncImage(
                    model = firstPhoto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 100f
                            )
                        )
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (entryWithDetails.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        entryWithDetails.tags.take(3).forEach { tag ->
                            Surface(modifier = Modifier.weight(1f, fill = false), color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(8.dp)) {
                                Text(tag.name, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                        if (entryWithDetails.tags.size > 3) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text("+${entryWithDetails.tags.size - 3}", style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                entry.placeName?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatDate(entry.date, LocalDateFormat.current),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyFeedState(hasFilters: Boolean, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (hasFilters) "Ничего не найдено" else "Пока нет записей", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(if (hasFilters) "Измените запрос или фильтры" else "Добавьте первое воспоминание о путешествии", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        TextButton(onClick = onAction) { Text(if (hasFilters) "Сбросить фильтры" else "Добавить запись") }
    }
}
