package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.database.UserPrefs
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.ui.theme.AppPalette
import com.mazzzuta.travellog.utils.DateFormatOption
import com.mazzzuta.travellog.utils.PhotoCleanup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(val prefs: UserPrefs? = null, val isLoading: Boolean = true,
    val isSaving: Boolean = false, val loadError: String? = null, val saveError: String? = null,
    val isCleaning: Boolean = false, val cleanupMessage: String? = null)

class SettingsViewModel(private val repository: UserPreferencesRepository, private val cleanup: PhotoCleanup) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private val writes = Mutex()

    init { load() }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                repository.preferencesFlow.collect { prefs -> _state.update { it.copy(prefs = prefs, isLoading = false) } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(isLoading = false, loadError = "Не удалось загрузить настройки") } }
        }
    }

    private fun save(onSaved: () -> Unit = {}, action: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                _state.update { it.copy(isSaving = true, saveError = null) }
                try { action(); onSaved() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { _state.update { it.copy(saveError = "Не удалось сохранить настройки. Повторите выбор") } }
                finally { _state.update { it.copy(isSaving = false) } }
            }
        }
    }

    fun setNickname(name: String, onSaved: () -> Unit = {}) {
        val trimmed = name.trim().take(30)
        if (trimmed.isNotBlank()) save(onSaved) { repository.setNickname(trimmed) }
    }
    fun setThemeMode(mode: ThemeMode) = save { repository.setThemeMode(mode) }
    fun setScheme(palette: AppPalette) = save { repository.setScheme(palette.id) }
    fun setDefaultSort(sort: SortOption) = save { repository.setDefaultSort(sort.sqlValue) }
    fun setDateFormat(option: DateFormatOption) = save { repository.setDateFormat(option.id) }

    fun cleanPhotos() {
        if (_state.value.isCleaning) return
        _state.update { it.copy(isCleaning = true, cleanupMessage = null) }
        viewModelScope.launch {
            try {
                val result = cleanup.clean()
                _state.update { it.copy(cleanupMessage = "Удалено фотографий: ${result.deleted}. Освобождено: ${"%.1f".format(result.bytes / 1048576.0)} МБ." +
                    if (result.failed > 0) " Не удалось удалить: ${result.failed}. Повторите очистку." else "") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(cleanupMessage = "Не удалось завершить очистку. Попробуйте ещё раз") } }
            finally { _state.update { it.copy(isCleaning = false) } }
        }
    }
}
