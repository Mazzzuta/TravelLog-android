package com.mazzzuta.travellog.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import com.mazzzuta.travellog.viewmodels.MapViewModel
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.mazzzuta.travellog.utils.LocalDateFormat
import com.mazzzuta.travellog.utils.formatDate
import com.mazzzuta.travellog.utils.hasInternetConnection
import com.mazzzuta.travellog.utils.observeInternetConnection
import kotlinx.coroutines.delay

@Composable
fun MapScreen(
    onEntryClick: (Long) -> Unit,
    focusEntryId: Long? = null,
    onBack: (() -> Unit)? = null,
    viewModel: MapViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val online by remember(context) { context.observeInternetConnection() }
        .collectAsState(initial = context.hasInternetConnection())
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }
    val focusEntry = remember(state.entries, focusEntryId) { state.entries.firstOrNull { it.entry.id == focusEntryId } }
    // Совпадающие координаты показываем одной меткой со списком записей
    val groups = remember(state.entries) { state.entries.groupBy { it.entry.latitude!! to it.entry.longitude!! } }
    val positions = remember(groups) { groups.keys.toList() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) viewModel.locate() else viewModel.showPermissionError()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Передаём события и освобождаем карту при уходе с экрана.
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    DisposableEffect(mapView) {
        val listener = MapView.OnDidFailLoadingMapListener { mapError = true }
        mapView.addOnDidFailLoadingMapListener(listener)
        onDispose { mapView.removeOnDidFailLoadingMapListener(listener) }
    }
    LaunchedEffect(map, online, loadAttempt) {
        val currentMap = map ?: return@LaunchedEffect
        if (loaded && !mapError) return@LaunchedEffect
        loaded = false
        mapError = false
        currentMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
            loaded = true
            mapError = false
        }
        delay(15_000)
        if (!loaded) mapError = true
    }
    LaunchedEffect(map, loaded, positions, state.isLoading) {
        val currentMap = map
        if (loaded && currentMap != null) {
            currentMap.removeAnnotations()
            positions.forEach { position ->
                currentMap.addMarker(MarkerOptions().position(LatLng(position.first, position.second)))
            }
        }
        if (loaded && currentMap != null && !state.isLoading && positions.isNotEmpty() && !cameraInitialized) {
            val update = if (focusEntry != null) {
                val position = focusEntry.entry.latitude!! to focusEntry.entry.longitude!!
                selected = position
                CameraUpdateFactory.newLatLngZoom(LatLng(position.first, position.second), 14.0)
            } else if (positions.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(positions[0].first, positions[0].second), 11.0)
            } else {
                val bounds = LatLngBounds.Builder()
                positions.forEach { bounds.include(LatLng(it.first, it.second)) }
                CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
            }
            currentMap.animateCamera(update)
            cameraInitialized = true
        }
    }
    LaunchedEffect(loaded, state.location) {
        state.location?.let { if (loaded) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.first, it.second), 14.0)) }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = {
            mapView.apply { getMapAsync { currentMap ->
                map = currentMap
                currentMap.uiSettings.setLogoEnabled(false)
                currentMap.setOnMarkerClickListener { marker ->
                    selected = marker.position.latitude to marker.position.longitude
                    true
                }
                currentMap.addOnMapClickListener { selected = null; false }
            } }
        })
        Card(Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) TextButton(onClick = onBack) { Text("Назад") }
                    Text("Карта", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(enabled = loaded && !state.isLocating, onClick = {
                        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                        if (granted) viewModel.locate() else permissions.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(if (state.isLocating) "Поиск…" else "Где я") }
                }
                if (state.isLoading || (!loaded && !mapError && online)) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.isLoading && state.entries.isEmpty()) Text("Пока нет мест. Добавьте геолокацию в запись.")
                if (!state.isLoading && focusEntryId != null && focusEntry == null) {
                    Text("У этой записи больше нет места на карте.")
                }
                if (!online) {
                    Text("Нет подключения к интернету. Для загрузки карты подключитесь к сети.", color = MaterialTheme.colorScheme.error)
                } else if (mapError) {
                    Text("Не удалось загрузить карту. Проверьте интернет.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { loadAttempt++ }) { Text("Повторить") }
                }
                state.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.load() }) { Text("Повторить загрузку записей") }
                }
            }
        }
        groups[selected]?.let { entries ->
            Card(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp).fillMaxWidth()) {
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entries.first().entry.placeName ?: "Выбранное место", Modifier.weight(1f))
                        TextButton(onClick = { selected = null }) { Text("Закрыть") }
                    }
                    entries.sortedByDescending { it.entry.id == focusEntryId }.forEach { item ->
                        val photo = item.photos.minWithOrNull(compareBy({ it.orderIndex }, { it.id }))
                        ListItem(
                            modifier = Modifier.clickable { onEntryClick(item.entry.id) },
                            headlineContent = { Text(item.entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text("${formatDate(item.entry.date, LocalDateFormat.current)} · Нажмите, чтобы открыть")
                            },
                            leadingContent = photo?.let { image -> {
                                AsyncImage(model = image.filePath, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                            } },
                        )
                    }
                }
            }
        }
    }
}
