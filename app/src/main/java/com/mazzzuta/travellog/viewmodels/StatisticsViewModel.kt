package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.StatisticsData
import com.mazzzuta.travellog.database.TagCount
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class TravelStatistics(
    val entries: Int,
    val trips: Int,
    val photos: Int,
    val places: Int,
    val monthsByYear: Map<Int, List<Int>>,
    val tags: List<TagCount>,
)

// Группируем по локальной дате дневника, а не по UTC из SQL strftime
internal fun calculateStatistics(data: StatisticsData, zone: ZoneId): TravelStatistics {
    val dates = data.dates.map { Instant.ofEpochMilli(it).atZone(zone) }
    val months = dates.groupBy { it.year }.mapValues { (_, datesInYear) ->
        val counts = datesInYear.groupingBy { it.monthValue }.eachCount()
        (1..12).map { counts[it] ?: 0 }
    }
    val tags = data.tags.filter { it.count > 0 }
        .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tagName })
    return TravelStatistics(data.dates.size, data.totals.trips, data.totals.photos, data.totals.places, months, tags)
}

data class StatisticsUiState(
    val statistics: TravelStatistics? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class StatisticsViewModel(private val entries: EntryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState = _uiState.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _uiState.value = StatisticsUiState()
        job = viewModelScope.launch {
            try {
                entries.observeStatistics().map { data ->
                    calculateStatistics(data, ZoneId.systemDefault())
                }.flowOn(Dispatchers.Default).collect { statistics ->
                    _uiState.value = StatisticsUiState(statistics, isLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = StatisticsUiState(isLoading = false, error = "Не удалось загрузить статистику")
            }
        }
    }
}
