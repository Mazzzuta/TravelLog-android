package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.database.EntryEntity
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.TagEntity
import com.mazzzuta.travellog.database.TripRepository
import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.utils.GeocoderHelper
import com.mazzzuta.travellog.utils.LocationHelper
import com.mazzzuta.travellog.utils.PhotoStorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import java.io.File

data class CreateEntryUiState(
    val date: Long = System.currentTimeMillis(),
    val availableTrips: List<TripEntity> = emptyList(),
    val tripId: Long? = null,
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
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
)

class CreateEntryViewModel(
    private val repository: EntryRepository,
    private val tripRepository: TripRepository,
    private val geocoderHelper: GeocoderHelper,
    private val locationHelper: LocationHelper,
    private val photoStorageHelper: PhotoStorageHelper,
    private val entryId: Long? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEntryUiState(isLoading = entryId != null))
    val uiState: StateFlow<CreateEntryUiState> = _uiState.asStateFlow()
    private var originalEntry: EntryEntity? = null
    private var originalPhotoPaths: Set<String> = emptySet()

    init {
        if (entryId != null) viewModelScope.launch {
            try {
                val details = repository.getEntryWithDetails(entryId).first()
                    ?: error("Запись не найдена")
                originalEntry = details.entry
                originalPhotoPaths = details.photos.map { it.filePath }.toSet()
                _uiState.update {
                    it.copy(
                        title = details.entry.title,
                        description = details.entry.description,
                        date = details.entry.date,
                        tripId = details.entry.tripId,
                        photoUris = details.photos.sortedBy { photo -> photo.orderIndex }.map { photo -> photo.filePath },
                        selectedTagIds = details.tags.map { tag -> tag.id }.toSet(),
                        latitude = details.entry.latitude,
                        longitude = details.entry.longitude,
                        placeName = details.entry.placeName,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = "Не удалось загрузить запись") }
            }
        }
        viewModelScope.launch {
            repository.getAllTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
        viewModelScope.launch {
            tripRepository.getAllTrips().collect { trips ->
                _uiState.update {
                    it.copy(availableTrips = trips, tripId = it.tripId ?: if (entryId == null) trips.firstOrNull()?.id else null)
                }
            }
        }
    }

    fun onTitleChanged(text: String) {
        _uiState.update { it.copy(title = text, titleError = if (text.isNotBlank()) null else it.titleError) }
    }

    fun onDescriptionChanged(text: String) {
        _uiState.update { it.copy(description = text) }
    }

    fun onDateChanged(date: Long) {
        _uiState.update { it.copy(date = date) }
    }

    fun onTripSelected(tripId: Long?) {
        _uiState.update { it.copy(tripId = tripId) }
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
        if (state.isLoading || state.loadError != null || state.isSaving || state.isSaved || state.isDetectingLocation) return
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Название записи не может быть пустым") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val copiedPaths = mutableListOf<String>()
            try {
                val savedPaths = state.photoUris.map { uri ->
                    if (uri in originalPhotoPaths) uri
                    else photoStorageHelper.saveToInternalStorage(uri).also { copiedPaths.add(it) }
                }
                val original = originalEntry
                val tripId = state.tripId ?: original?.tripId ?: tripRepository.ensureDefaultTrip()
                val entry = (original ?: EntryEntity(
                    tripId = tripId,
                    title = "", description = "", date = state.date,
                    latitude = 0.0, longitude = 0.0,
                )).copy(
                    title = state.title.trim(), description = state.description.trim(),
                    date = state.date,
                    tripId = tripId,
                    latitude = state.latitude ?: 0.0, longitude = state.longitude ?: 0.0,
                    placeName = state.placeName, updatedAt = System.currentTimeMillis(),
                )
                if (original == null) repository.createEntry(entry, savedPaths, state.selectedTagIds.toList())
                else repository.updateEntry(entry, savedPaths, state.selectedTagIds.toList())
                // shortcut: убранные старые фото остаются на диске; очистку добавить с учётом обложек поездок.
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: CancellationException) {
                // Транзакция откатывается при отмене; новые файлы больше не нужны.
                copiedPaths.forEach { File(it).delete() }
                throw e
            } catch (e: Exception) {
                copiedPaths.forEach { File(it).delete() }
                _uiState.update { it.copy(isSaving = false, saveError = "Не удалось сохранить запись. Попробуйте ещё раз") }
            }
        }
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveError = null) }
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
