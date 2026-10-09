package com.mazzzuta.travellog.viewmodels

import com.mazzzuta.travellog.database.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import com.mazzzuta.travellog.database.TripRepository
import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.utils.endOfDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


enum class SortOption(val sqlValue: String, val label: String) {
    DATE_DESC("date_desc", "Сначала новые"),
    DATE_ASC("date_asc", "Сначала старые"),
    TITLE("title", "По названию");

    companion object {
        fun fromSql(value: String) = entries.firstOrNull { it.sqlValue == value } ?: DATE_DESC
    }
}

data class FeedUiState(
    val entries: List<EntryWithDetails> = emptyList(),
    val query: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val isLoading: Boolean = true,
    val trips: List<TripEntity> = emptyList(),
    val tripId: Long? = null,
    val onlyWithoutTrip: Boolean = false,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val repository: EntryRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(FeedUiState())

    init {
        // стартовая сортировка берётся из настроек
        viewModelScope.launch {
            filters.update { it.copy(sortOption = SortOption.fromSql(prefsRepository.preferencesFlow.first().defaultSort)) }
        }
    }

    val uiState: StateFlow<FeedUiState> = combine(filters, tripRepository.getAllTrips()) { filter, trips ->
        // Удалённая поездка больше не должна оставлять ленту с невидимым фильтром.
        filter.copy(trips = trips, tripId = filter.tripId?.takeIf { id -> trips.any { it.id == id } })
    }.flatMapLatest { filter ->
        repository.searchEntries(query = filter.query, tripId = filter.tripId,
            dateFrom = filter.dateFrom, dateTo = filter.dateTo, sortBy = filter.sortOption.sqlValue,
            onlyWithoutTrip = filter.onlyWithoutTrip)
            .map { filter.copy(entries = it, isLoading = false) }
            .onStart { emit(filter.copy(isLoading = true)) }
            .catch { emit(filter.copy(isLoading = false, error = "Не удалось загрузить записи")) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeedUiState()
        )

    fun onSearchChanged(text: String) {
        filters.update { it.copy(query = text) }
    }

    fun onSortChanged(option: SortOption) {
        filters.update { it.copy(sortOption = option) }
    }

    fun onTripFilterChanged(tripId: Long?, onlyWithoutTrip: Boolean = false) {
        filters.update { it.copy(tripId = tripId, onlyWithoutTrip = onlyWithoutTrip) }
    }

    fun onDateRangeChanged(from: Long, to: Long) {
        require(from <= to)
        filters.update { it.copy(dateFrom = from, dateTo = endOfDayMillis(to)) }
    }

    fun clearFilters() {
        filters.update { FeedUiState(sortOption = it.sortOption) }
    }
}
