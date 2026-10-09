package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import com.mazzzuta.travellog.database.hasCoordinates
import com.mazzzuta.travellog.utils.LocationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val entries: List<EntryWithDetails> = emptyList(),
    val isLoading: Boolean = true,
    val isLocating: Boolean = false,
    val location: Pair<Double, Double>? = null,
    val error: String? = null,
)

class MapViewModel(private val repository: EntryRepository, private val locationHelper: LocationHelper) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                repository.searchEntries().collect { entries ->
                    _uiState.update { it.copy(entries = entries.filter { entry -> entry.entry.hasCoordinates() }, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Не удалось загрузить места. Попробуйте ещё раз") }
            }
        }
    }

    fun locate() {
        if (_uiState.value.isLocating) return
        _uiState.update { it.copy(isLocating = true, location = null, error = null) }
        viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                _uiState.update { it.copy(isLocating = false,
                    location = location?.let { position -> position.latitude to position.longitude },
                    error = if (location == null) "Не удалось определить положение. Проверьте геолокацию" else null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLocating = false, error = "Не удалось определить положение") }
            }
        }
    }

    fun showPermissionError() {
        _uiState.update { it.copy(error = "Геолокация не разрешена. Сохранённые места доступны на карте") }
    }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
