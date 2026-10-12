package com.mazzzuta.travellog.ui.entrydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mazzzuta.travellog.viewmodels.EntryDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.mazzzuta.travellog.utils.LocalDateFormat
import com.mazzzuta.travellog.utils.formatDate
import com.mazzzuta.travellog.database.hasCoordinates

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    entryId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMapClick: () -> Unit,
    viewModel: EntryDetailViewModel = koinViewModel(parameters = { parametersOf(entryId) })
) {
    val state by viewModel.uiState.collectAsState()
    var fullscreenPage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    val entryWithDetails = state.entry
    if (state.isLoading || state.isDeleted || (state.isDeleting && entryWithDetails == null)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.loadError != null || entryWithDetails == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.loadError ?: "Запись не найдена", style = MaterialTheme.typography.titleMedium)
            if (state.loadError != null) TextButton(onClick = viewModel::load) { Text("Повторить") }
            TextButton(onClick = onBack) { Text("Назад") }
        }
        return
    }

    val entry = entryWithDetails.entry
    val photos = entryWithDetails.photos

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // Галерея фото
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            if (photos.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { photos.size })
                val scope = rememberCoroutineScope()

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = photos[page].filePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { fullscreenPage = page }
                    )
                }

                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .pointerInput(photos.size) {
                                detectTapGestures { offset ->
                                    val page = (offset.x / size.width * photos.size).toInt()
                                        .coerceIn(0, photos.size - 1)
                                    scope.launch { pagerState.animateScrollToPage(page) }
                                }
                            }
                            .pointerInput(photos.size) {
                                detectHorizontalDragGestures { change, _ ->
                                    val page = (change.position.x / size.width * photos.size).toInt()
                                        .coerceIn(0, photos.size - 1)
                                    scope.launch { pagerState.scrollToPage(page) }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        photos.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == pagerState.currentPage) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                        else Color.White.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        }

        // Содержимое записи
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            if (entryWithDetails.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entryWithDetails.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(
                entry.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                formatDate(entry.date, LocalDateFormat.current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(state.tripTitle?.let { "Поездка: $it" } ?: "Без поездки",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            entry.placeName?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (entry.hasCoordinates()) {
                TextButton(onClick = onMapClick, enabled = !state.isDeleting) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("На карте")
                }
            }

            Text(entry.description, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !state.isDeleting,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Изменить")
                }
                FilledTonalButton(
                    onClick = { viewModel.onDeleteClick() },
                    enabled = !state.isDeleting,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }
        }
    }

    // Полноэкранный просмотр фото
    fullscreenPage?.takeIf { it in photos.indices }?.let { startPage ->
        FullscreenPhotoViewer(
            photoPaths = photos.map { it.filePath },
            startPage = startPage,
            onDismiss = { fullscreenPage = null }
        )
    }

    // Подтверждение удаления
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onDeleteDismiss() },
            title = { Text("Удалить запись?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Это действие нельзя отменить.")
                    if (state.isDeleting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.isDeleting, onClick = { viewModel.onDeleteConfirm() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !state.isDeleting, onClick = { viewModel.onDeleteDismiss() }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun FullscreenPhotoViewer(
    photoPaths: List<String>,
    startPage: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val pagerState = rememberPagerState(initialPage = startPage, pageCount = { photoPaths.size })

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = photoPaths[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${photoPaths.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}
