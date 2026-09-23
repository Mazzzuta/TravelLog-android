package com.mazzzuta.travellog.ui.feed

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun FeedScreen(
    onEntryClick: (Long) -> Unit,
    onTripsClick: () -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 48.dp, bottom = 12.dp)) {
            Text(
                text = "TRAVEL LOG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Мои записи",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onTripsClick, label = { Text("Поездки") })

                var sortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { sortMenuExpanded = true },
                        label = { Text(state.sortOption.label) }
                    )
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    viewModel.onSortChanged(option)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.entries.isEmpty()) {
            EmptyFeedState()
        } else {
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

@Composable
private fun EntryCard(entryWithDetails: EntryWithDetails, onClick: () -> Unit) {
    val entry = entryWithDetails.entry
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (entryWithDetails.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entryWithDetails.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            entry.placeName?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun EmptyFeedState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Попробуй изменить запрос", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}