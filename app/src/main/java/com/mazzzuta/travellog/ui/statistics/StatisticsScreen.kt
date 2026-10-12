package com.mazzzuta.travellog.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazzzuta.travellog.viewmodels.StatisticsViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import java.time.Year
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatisticsScreen(onCreateClick: () -> Unit, viewModel: StatisticsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var chosenYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var yearMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Статистика", style = MaterialTheme.typography.headlineSmall)
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::load) { Text("Повторить") }
            }
            else -> state.statistics?.let { statistics ->
                Text("За всё время", style = MaterialTheme.typography.titleMedium)
                listOf("Записи" to statistics.entries, "Поездки" to statistics.trips,
                    "Фотографии" to statistics.photos, "Места на карте" to statistics.places)
                    .chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label, value) ->
                                Card(Modifier.weight(1f)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                if (statistics.entries == 0) {
                    Text("Пока нет записей. Добавьте первое путешествие — здесь появятся график и любимые теги.")
                    Button(onClick = onCreateClick) { Text("Добавить запись") }
                } else {
                    val years = (statistics.monthsByYear.keys + Year.now().value).sortedDescending()
                    val year = chosenYear?.takeIf { it in years } ?: statistics.monthsByYear.keys.max()
                    val counts = statistics.monthsByYear[year] ?: List(12) { 0 }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Записи по месяцам", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Box {
                                    TextButton(onClick = { yearMenu = true }) { Text("$year ▾") }
                                    DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                                        years.forEach { option ->
                                            DropdownMenuItem(text = { Text(option.toString()) }, onClick = {
                                                chosenYear = option
                                                yearMenu = false
                                            })
                                        }
                                    }
                                }
                            }
                            if (counts.all { it == 0 }) Text("В $year году записей пока нет.")
                            else MonthlyChart(counts, year)
                            Text("Записей за $year год: ${counts.sum()}")
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Частые теги", style = MaterialTheme.typography.titleMedium)
                            if (statistics.tags.isEmpty()) Text("Добавьте теги к записям, чтобы увидеть любимые темы.")
                            else {
                                statistics.tags.take(5).forEach { tag ->
                                    Row {
                                        Text(tag.tagName, Modifier.weight(1f))
                                        Text(tag.count.toString())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyChart(counts: List<Int>, year: Int) {
    val months = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(counts) { producer.runTransaction { columnSeries { series(counts) } } }
    val formatter = remember { CartesianValueFormatter { _, value, _ -> months.getOrElse(value.toInt()) { "" } } }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(rememberColumnCartesianLayer(columnCollectionSpacing = 40.dp),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = CartesianValueFormatter { _, value, _ -> value.toInt().toString() },
                    itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { 1.0 }) }),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = formatter,
                    labelRotationDegrees = 0f,
                    itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 1 }, addExtremeLabelPadding = true) })),
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            zoomState = rememberVicoZoomState(
                zoomEnabled = false, initialZoom = Zoom.fixed(1f),
                minZoom = Zoom.fixed(1f), maxZoom = Zoom.fixed(1f)),
            modifier = Modifier.fillMaxWidth().height(240.dp).semantics {
                contentDescription = "Записи за $year год: " + months.zip(counts).joinToString { "${it.first}: ${it.second}" }
            },
        )
    }
}
