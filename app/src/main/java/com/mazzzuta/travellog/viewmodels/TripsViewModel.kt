package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.database.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripsUiState(
    val trips: List<TripEntity> = emptyList(),
    val entries: List<EntryWithDetails> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val tripToDelete: TripEntity? = null,
    val isDeleting: Boolean = false,
)

class TripsViewModel(
    private val repository: TripRepository,
    private val entryRepository: EntryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                combine(repository.getAllTrips(), entryRepository.searchEntries()) { trips, entries ->
                    trips to entries
                }.collect { (trips, entries) ->
                    _uiState.update { it.copy(trips = trips, entries = entries, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Не удалось загрузить поездки") }
            }
        }
    }

    fun save(trip: TripEntity) {
        if (_uiState.value.isSaving || _uiState.value.isDeleting) return
        _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
        viewModelScope.launch {
            try {
                if (trip.id == 0L) repository.createTrip(trip) else repository.updateTrip(trip)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Не удалось сохранить поездку. Попробуйте ещё раз") }
            }
        }
    }

    fun requestDelete(trip: TripEntity) {
        if (!_uiState.value.isSaving && !_uiState.value.isDeleting) {
            _uiState.update { it.copy(tripToDelete = trip, error = null) }
        }
    }

    fun dismissDelete() {
        if (!_uiState.value.isDeleting) _uiState.update { it.copy(tripToDelete = null, error = null) }
    }

    fun confirmDelete(deleteEntries: Boolean) {
        val state = _uiState.value
        val trip = state.tripToDelete ?: return
        if (state.isDeleting || state.isSaving) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.deleteTrip(trip, deleteEntries)
                _uiState.update { it.copy(isDeleting = false, tripToDelete = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isDeleting = false, error = "Не удалось удалить поездку. Попробуйте ещё раз") }
            }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(error = null, saved = false) } }
}
