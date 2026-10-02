package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryDetailUiState(
    val entry: EntryWithDetails? = null,
    val isLoading: Boolean = true,
    val showDeleteConfirmation: Boolean = false,
    val isDeleted: Boolean = false,
)

class EntryDetailViewModel(
    private val entryId: Long,
    private val repository: EntryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getEntryWithDetails(entryId).collect { entry ->
                _uiState.update { it.copy(entry = entry, isLoading = false) }
            }
        }
    }

    fun onDeleteClick() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun onDeleteDismiss() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteConfirm() {
        viewModelScope.launch {
            _uiState.value.entry?.let { repository.deleteEntry(it.entry) }
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}