package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryEntity
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.TagEntity
import com.mazzzuta.travellog.database.TripRepository
import com.mazzzuta.travellog.utils.GeocoderHelper
import com.mazzzuta.travellog.utils.LocationHelper
import com.mazzzuta.travellog.utils.PhotoStorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateEntryUiState(
    val title: String = "",
    val description: String = "",
    val photoUris: List<String> = emptyList(),
    val availableTags: List<TagEntity> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val newTagName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val isDetectingLocation: Boolean = false,
    val titleError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val locationSearchError: String? = null,
    val duplicatePhotosSkipped: Int = 0,
)

class CreateEntryViewModel(
    private val repository: EntryRepository,
    private val tripRepository: TripRepository,
    private val geocoderHelper: GeocoderHelper,
    private val locationHelper: LocationHelper,
    private val photoStorageHelper: PhotoStorageHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEntryUiState())
    val uiState: StateFlow<CreateEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    fun onTitleChanged(text: String) {
        _uiState.update { it.copy(title = text, titleError = if (text.isNotBlank()) null else it.titleError) }
    }

    fun onDescriptionChanged(text: String) {
        _uiState.update { it.copy(description = text) }
    }

    fun onPhotosPicked(uris: List<String>) {
        _uiState.update { state ->
            val newOnes = uris.filter { it !in state.photoUris }
            state.copy(
                photoUris = state.photoUris + newOnes,
                duplicatePhotosSkipped = uris.size - newOnes.size
            )
        }
    }

    fun onPhotoRemoved(uri: String) {
        _uiState.update { it.copy(photoUris = it.photoUris - uri) }
    }

    fun onDuplicateMessageShown() {
        _uiState.update { it.copy(duplicatePhotosSkipped = 0) }
    }

    fun toggleTag(tagId: Long) {
        _uiState.update {
            val newSet = if (tagId in it.selectedTagIds) it.selectedTagIds - tagId else it.selectedTagIds + tagId
            it.copy(selectedTagIds = newSet)
        }
    }

    fun onNewTagNameChanged(text: String) {
        _uiState.update { it.copy(newTagName = text) }
    }

    fun addNewTag() {
        val name = _uiState.value.newTagName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createTag(name)
            _uiState.update { it.copy(selectedTagIds = it.selectedTagIds + id, newTagName = "") }
        }
    }

    fun detectLocation() {
        viewModelScope.launch {

            _uiState.update { it.copy(isDetectingLocation = true) }
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                val placeName = geocoderHelper.getPlaceName(location.latitude, location.longitude)
                _uiState.update {
                    it.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        placeName = placeName ?: "Неизвестное место",
                        isDetectingLocation = false
                    )
                }
            } else {
                _uiState.update { it.copy(isDetectingLocation = false) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Название записи не может быть пустым") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val savedPaths = state.photoUris.map { photoStorageHelper.saveToInternalStorage(it) }
            val tripId = tripRepository.ensureDefaultTrip()
            val entry = EntryEntity(
                tripId = tripId,
                title = state.title.trim(),
                description = state.description.trim(),
                date = System.currentTimeMillis(),
                latitude = state.latitude ?: 0.0,
                longitude = state.longitude ?: 0.0,
                placeName = state.placeName
            )
            repository.createEntry(entry, savedPaths, state.selectedTagIds.toList())
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun searchLocationByName(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetectingLocation = true, locationSearchError = null) }
            val result = geocoderHelper.searchPlace(query)
            if (result != null) {
                val (lat, lng, name) = result
                _uiState.update {
                    it.copy(latitude = lat, longitude = lng, placeName = name, isDetectingLocation = false)
                }
            } else {
                _uiState.update {
                    it.copy(isDetectingLocation = false, locationSearchError = "Место не найдено, попробуй уточнить запрос")
                }
            }
        }
    }
}