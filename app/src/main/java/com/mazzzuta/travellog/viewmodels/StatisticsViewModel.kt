package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import com.mazzzuta.travellog.database.TagCount
import com.mazzzuta.travellog.database.TripRepository
import com.mazzzuta.travellog.database.hasCoordinates
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
internal fun calculateStatistics(entries: List<EntryWithDetails>, tripCount: Int, zone: ZoneId): TravelStatistics {
    val dates = entries.map { Instant.ofEpochMilli(it.entry.date).atZone(zone) }
    val months = dates.groupBy { it.year }.mapValues { (_, datesInYear) ->
        val counts = datesInYear.groupingBy { it.monthValue }.eachCount()
        (1..12).map { counts[it] ?: 0 }
    }
    // Для каждого тега считаем записи с ним
    val tags = entries.flatMap { it.tags.distinctBy { tag -> tag.id } }
        .groupBy { it.id }.values.map { TagCount(it.first().name, it.size) }
        .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tagName })
    return TravelStatistics(entries.size, tripCount, entries.sumOf { it.photos.size },
        entries.filter { it.entry.hasCoordinates() }.map { it.entry.latitude to it.entry.longitude }.distinct().size,
        months, tags)
}

data class StatisticsUiState(
    val statistics: TravelStatistics? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class StatisticsViewModel(private val entries: EntryRepository, private val trips: TripRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState = _uiState.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _uiState.value = StatisticsUiState()
        job = viewModelScope.launch {
            try {
                combine(entries.searchEntries(), trips.getAllTrips()) { entries, trips ->
                    calculateStatistics(entries, trips.size, ZoneId.systemDefault())
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
