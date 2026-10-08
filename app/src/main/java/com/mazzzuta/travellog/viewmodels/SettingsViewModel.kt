package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.database.UserPrefs
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.ui.theme.AppPalette
import com.mazzzuta.travellog.utils.DateFormatOption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {

    /** null, пока настройки не загружены. */
    val prefs: StateFlow<UserPrefs?> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setNickname(name: String) {
        val trimmed = name.trim().take(30)
        if (trimmed.isNotBlank()) viewModelScope.launch { repository.setNickname(trimmed) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    /** Схема запоминается отдельно для светлого и тёмного режима. */
    fun setScheme(palette: AppPalette) {
        viewModelScope.launch {
            if (palette.isDark) repository.setDarkScheme(palette.id) else repository.setLightScheme(palette.id)
        }
    }

    fun setDefaultSort(sort: SortOption) {
        viewModelScope.launch { repository.setDefaultSort(sort.sqlValue) }
    }

    fun setDateFormat(option: DateFormatOption) {
        viewModelScope.launch { repository.setDateFormat(option.id) }
    }
}