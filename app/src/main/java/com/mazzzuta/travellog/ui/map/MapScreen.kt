package com.mazzzuta.travellog.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.maplibre.android.maps.MapView
import com.mazzzuta.travellog.viewmodels.MapViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MapScreen(onEntryClick: (Long) -> Unit, viewModel: MapViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val groups = state.entries.groupBy { it.entry.latitude!! to it.entry.longitude!! }
    val positions = groups.keys.toList()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) viewModel.locate() else viewModel.showPermissionError()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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
    LaunchedEffect(map, loaded, positions) {
        val currentMap = map
        if (loaded && currentMap != null) {
            currentMap.removeAnnotations()
            positions.forEach { position ->
                currentMap.addMarker(MarkerOptions().position(LatLng(position.first, position.second)))
            }
        }
        if (loaded && currentMap != null && positions.isNotEmpty()) {
            val update = if (positions.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(positions[0].first, positions[0].second), 11.0)
            } else {
                val bounds = LatLngBounds.Builder()
                positions.forEach { bounds.include(LatLng(it.first, it.second)) }
                CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
            }
            currentMap.animateCamera(update)
        }
    }
    LaunchedEffect(loaded, state.location) {
        state.location?.let { if (loaded) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.first, it.second), 14.0)) }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = {
            mapView.apply { getMapAsync { currentMap ->
                map = currentMap
                currentMap.uiSettings.setLogoEnabled(false)
                currentMap.setOnMarkerClickListener { marker ->
                    selected = marker.position.latitude to marker.position.longitude
                    true
                }
                currentMap.addOnMapClickListener { selected = null; false }
                currentMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    loaded = true
                    mapError = false
                }
            } }
        })
        Card(Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Карта", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(enabled = loaded && !state.isLocating, onClick = {
                        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                        if (granted) viewModel.locate() else permissions.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(if (state.isLocating) "Поиск…" else "Где я") }
                }
                if (state.isLoading || (!loaded && !mapError)) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.isLoading && state.entries.isEmpty()) Text("Пока нет мест. Добавьте геолокацию в запись.")
                if (mapError) {
                    Text("Не удалось загрузить карту. Проверьте интернет.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        mapError = false
                        loaded = false
                        map?.setStyle("https://tiles.openfreemap.org/styles/liberty") { loaded = true }
                    }) { Text("Повторить") }
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
                    entries.forEach { item ->
                        TextButton(onClick = { onEntryClick(item.entry.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${item.entry.title} · Открыть запись")
                        }
                    }
                }
            }
        }
    }
}
