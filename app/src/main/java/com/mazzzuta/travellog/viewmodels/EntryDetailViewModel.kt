package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import com.mazzzuta.travellog.database.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class EntryDetailUiState(
    val entry: EntryWithDetails? = null,
    val isLoading: Boolean = true,
    val showDeleteConfirmation: Boolean = false,
    val isDeleted: Boolean = false,
    val tripTitle: String? = null,
    val isDeleting: Boolean = false,
    val loadError: String? = null,
    val deleteError: String? = null,
)

class EntryDetailViewModel(
    private val entryId: Long,
    private val repository: EntryRepository,
    private val trips: TripRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                combine(repository.getEntryWithDetails(entryId), trips.getAllTrips()) { entry, allTrips ->
                    entry?.copy(photos = entry.photos.sortedWith(compareBy({ it.orderIndex }, { it.id }))) to
                        allTrips.firstOrNull { it.id == entry?.entry?.tripId }?.title
                }.collect { (entry, tripTitle) ->
                    _uiState.update { it.copy(entry = entry, tripTitle = tripTitle, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = "Не удалось загрузить запись") }
            }
        }
    }

    fun onDeleteClick() {
        if (_uiState.value.isDeleting || _uiState.value.entry == null) return
        _uiState.update { it.copy(showDeleteConfirmation = true, deleteError = null) }
    }

    fun onDeleteDismiss() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = false, deleteError = null) }
    }

    fun onDeleteConfirm() {
        val state = _uiState.value
        val entry = state.entry ?: return
        if (state.isDeleting || state.isDeleted || !state.showDeleteConfirmation) return
        _uiState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                repository.deleteEntry(entry.entry)
                _uiState.update { it.copy(isDeleted = true, isDeleting = false, showDeleteConfirmation = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isDeleting = false, deleteError = "Не удалось удалить запись. Попробуйте ещё раз") }
            }
        }
    }
}
