package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.EntryWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class SortOption(val sqlValue: String, val label: String) {
    DATE_DESC("date_desc", "Сначала новые"),
    DATE_ASC("date_asc", "Сначала старые"),
    TITLE("title", "По названию"),
}

data class FeedUiState(
    val entries: List<EntryWithDetails> = emptyList(),
    val query: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val isLoading: Boolean = true,
)

class FeedViewModel(private val repository: EntryRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sortOption = MutableStateFlow(SortOption.DATE_DESC)

    val uiState: StateFlow<FeedUiState> = combine(query, sortOption) { q, sort ->
        q to sort
    }.flatMapLatest { (q, sort) ->
        repository.searchEntries(query = q, sortBy = sort.sqlValue)
    }.combine(query) { entries, q -> entries to q }
        .combine(sortOption) { (entries, q), sort ->
            FeedUiState(entries = entries, query = q, sortOption = sort, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeedUiState()
        )

    fun onSearchChanged(text: String) {
        query.value = text
    }

    fun onSortChanged(option: SortOption) {
        sortOption.value = option
    }
}