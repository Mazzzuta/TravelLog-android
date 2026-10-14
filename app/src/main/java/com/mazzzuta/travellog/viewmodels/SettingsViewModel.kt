package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isCleaning: Boolean = false, val cleanupMessage: String? = null,
    val tags: List<TagEntity> = emptyList(), val isLoadingTags: Boolean = false, val tagsError: String? = null,
    val isDeletingTags: Boolean = false, val isResetting: Boolean = false, val resetError: String? = null)

class SettingsViewModel(private val repository: UserPreferencesRepository, private val cleanup: PhotoCleanup, private val entries: EntryRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var tagsJob: Job? = null
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
            // Быстрые изменения сохраняем по очереди; поздняя запись не должна перекрыть более новый выбор.
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
    fun setAvatar(uri: android.net.Uri?) = save { repository.setAvatar(uri) }
    fun setScheme(palette: AppPalette) = save { repository.setScheme(palette.id) }
    fun setDefaultSort(sort: SortOption) = save { repository.setDefaultSort(sort.sqlValue) }
    fun setDateFormat(option: DateFormatOption) = save { repository.setDateFormat(option.id) }

    fun loadTags() {
        tagsJob?.cancel()
        _state.update { it.copy(isLoadingTags = true, tagsError = null) }
        tagsJob = viewModelScope.launch {
            try { entries.getAllTags().collect { tags -> _state.update { it.copy(tags = tags, isLoadingTags = false) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(isLoadingTags = false, tagsError = "Не удалось загрузить теги") } }
        }
    }

    fun deleteTags(ids: List<Long>, onDeleted: () -> Unit) {
        if (ids.isEmpty() || _state.value.isDeletingTags || _state.value.isResetting) return
        _state.update { it.copy(isDeletingTags = true, tagsError = null) }
        viewModelScope.launch {
            try { entries.deleteTags(ids); onDeleted() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(tagsError = "Не удалось удалить теги. Попробуйте ещё раз") } }
            finally { _state.update { it.copy(isDeletingTags = false) } }
        }
    }

    fun clearResetError() { _state.update { it.copy(resetError = null) } }

    fun clearAllData() {
        val state = _state.value
        if (state.isResetting || state.isSaving || state.isCleaning || state.isDeletingTags) return
        _state.update { it.copy(isResetting = true, resetError = null) }
        viewModelScope.launch {
            try {
                check(withContext(Dispatchers.IO) { repository.clearAllData() })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(resetError = "Не удалось удалить данные. Попробуйте ещё раз") } }
            finally { _state.update { it.copy(isResetting = false) } }
        }
    }

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
