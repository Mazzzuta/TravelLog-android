package com.mazzzuta.travellog.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    val hasChanges: Boolean = false,
    val isAddingTag: Boolean = false,
    val formError: String? = null,
    val locationFound: Boolean = false,
)

class CreateEntryViewModel(
    private val repository: EntryRepository,
    private val tripRepository: TripRepository,
    private val geocoderHelper: GeocoderHelper,
    private val locationHelper: LocationHelper,
    private val photoStorageHelper: PhotoStorageHelper,
    private val entryId: Long? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEntryUiState(isLoading = entryId != null))
    val uiState: StateFlow<CreateEntryUiState> = _uiState.asStateFlow()
    private var originalEntry: EntryEntity? = null
    private var originalPhotoPaths: Set<String> = emptySet()
    private var loadJob: Job? = null
    private var locationJob: Job? = null
    private var locationVersion = 0

    private fun restoreDraft(state: CreateEntryUiState): CreateEntryUiState =
        savedStateHandle.get<String>("draft")?.let { encoded ->
            runCatching { Json.decodeFromString<EntryDraft>(encoded).applyTo(state) }.getOrNull()
        } ?: state

    private fun edit(change: (CreateEntryUiState) -> CreateEntryUiState) {
        if (_uiState.value.isLoading || _uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.update {
            val next = change(it)
            next.copy(hasChanges = it.hasChanges || next.draft() != it.draft())
        }
        if (_uiState.value.hasChanges) {
            val encoded = _uiState.value.draft().encodeForSavedState()
            if (encoded != null) savedStateHandle["draft"] = encoded
            else {
                savedStateHandle.remove<String>("draft")
                _uiState.update { it.copy(formError = "Черновик слишком большой для восстановления системой. Сохраните запись перед выходом из приложения") }
            }
        }
    }

    init {
        if (entryId != null) loadEntry() else _uiState.value = restoreDraft(_uiState.value)
        viewModelScope.launch {
            try {
                repository.getAllTags().collect { tags -> _uiState.update { it.copy(availableTags = tags) } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(formError = "Не удалось загрузить теги. Откройте форму заново") } }
        }
        viewModelScope.launch {
            try {
                tripRepository.getAllTrips().collect { trips -> _uiState.update { it.copy(availableTrips = trips) } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(formError = "Не удалось загрузить поездки. Откройте форму заново") } }
        }
    }

    fun loadEntry() {
        val id = entryId ?: return
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val details = repository.getEntryWithDetails(id).first()
                    ?: error("Запись не найдена")
                originalEntry = details.entry
                originalPhotoPaths = details.photos.map { it.filePath }.toSet()
                _uiState.update {
                    restoreDraft(it.copy(
                        title = details.entry.title,
                        description = details.entry.description,
                        date = details.entry.date,
                        tripId = details.entry.tripId,
                        photoUris = details.photos.sortedWith(compareBy({ photo -> photo.orderIndex }, { photo -> photo.id })).map { photo -> photo.filePath },
                        selectedTagIds = details.tags.map { tag -> tag.id }.toSet(),
                        latitude = details.entry.latitude,
                        longitude = details.entry.longitude,
                        placeName = details.entry.placeName,
                        isLoading = false,
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = "Не удалось загрузить запись") }
            }
        }
    }

    fun onTitleChanged(text: String) {
        edit { it.copy(title = text, titleError = if (text.isNotBlank()) null else it.titleError) }
    }

    fun onDescriptionChanged(text: String) {
        edit { it.copy(description = text) }
    }

    fun onDateChanged(date: Long) {
        edit { it.copy(date = date) }
    }

    fun onTripSelected(tripId: Long?) {
        edit { it.copy(tripId = tripId) }
    }

    fun onPhotosPicked(uris: List<String>) {
        edit { state ->
            val newOnes = uris.distinct().filter { it !in state.photoUris }
            state.copy(
                photoUris = state.photoUris + newOnes,
                duplicatePhotosSkipped = uris.size - newOnes.size
            )
        }
    }

    fun onPhotoRemoved(uri: String) {
        edit { it.copy(photoUris = it.photoUris - uri) }
    }

    fun onDuplicateMessageShown() {
        _uiState.update { it.copy(duplicatePhotosSkipped = 0) }
    }

    fun toggleTag(tagId: Long) {
        edit {
            val newSet = if (tagId in it.selectedTagIds) it.selectedTagIds - tagId else it.selectedTagIds + tagId
            it.copy(selectedTagIds = newSet)
        }
    }

    fun onNewTagNameChanged(text: String) {
        edit { it.copy(newTagName = text) }
    }

    fun addNewTag() {
        val name = _uiState.value.newTagName.trim()
        if (name.isBlank() || _uiState.value.isAddingTag || _uiState.value.isSaving) return
        _uiState.update { it.copy(isAddingTag = true) }
        viewModelScope.launch {
            try {
                val id = repository.createTag(name)
                edit { it.copy(selectedTagIds = it.selectedTagIds + id, newTagName = if (it.newTagName.trim() == name) "" else it.newTagName) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(formError = "Не удалось добавить тег. Попробуйте ещё раз") } }
            finally { _uiState.update { it.copy(isAddingTag = false) } }
        }
    }

    fun detectLocation() = findLocation {
        val location = locationHelper.getCurrentLocation() ?: return@findLocation null
        val name = withTimeoutOrNull(8_000) { geocoderHelper.getPlaceName(location.latitude, location.longitude) }
        Triple(location.latitude, location.longitude, name ?: "Неизвестное место")
    }

    private fun findLocation(search: suspend () -> Triple<Double, Double, String>?) {
        if (_uiState.value.isSaving || _uiState.value.isDetectingLocation) return
        val version = ++locationVersion
        _uiState.update { it.copy(isDetectingLocation = true, locationSearchError = null, locationFound = false) }
        locationJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(30_000) { search() }
                if (result == null) _uiState.update { it.copy(locationSearchError = "Не удалось найти место. Проверьте геолокацию и интернет или уточните запрос") }
                else edit { it.copy(latitude = result.first, longitude = result.second, placeName = result.third, locationFound = true) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(locationSearchError = "Ошибка поиска места. Попробуйте ещё раз") } }
            finally { if (version == locationVersion) _uiState.update { it.copy(isDetectingLocation = false) } }
        }
    }

    fun cancelLocationSearch() {
        locationVersion++
        locationJob?.cancel()
        _uiState.update { it.copy(isDetectingLocation = false, locationFound = false, locationSearchError = null) }
    }

    fun clearLocation() {
        cancelLocationSearch()
        edit { it.copy(latitude = null, longitude = null, placeName = null) }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(formError = "Нет разрешения на геолокацию. Место можно найти вручную") }
    }

    fun onFormErrorShown() { _uiState.update { it.copy(formError = null) } }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.loadError != null || state.isSaving || state.isSaved || state.isDetectingLocation || state.isAddingTag) return
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Название записи не может быть пустым") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            photoStorageHelper.withPhotoAccess {
                val copiedPaths = mutableListOf<String>()
                var committed = false
                try {
                    val savedPaths = state.photoUris.map { uri ->
                        if (uri in originalPhotoPaths) uri.also { check(File(it).isFile) { "Фотография недоступна" } }
                        else photoStorageHelper.saveToInternalStorage(uri).also { copiedPaths.add(it) }
                    }
                    val original = originalEntry
                    val tripId = state.tripId
                    val entry = (original ?: EntryEntity(
                        tripId = tripId,
                        title = "", description = "", date = state.date,
                    )).copy(
                        title = state.title.trim(), description = state.description.trim(),
                        date = state.date,
                        tripId = tripId,
                        latitude = state.latitude, longitude = state.longitude,
                        placeName = state.placeName, updatedAt = System.currentTimeMillis(),
                    )
                    withContext(NonCancellable) {
                        if (original == null) repository.createEntry(entry, savedPaths, state.selectedTagIds.toList())
                        else repository.updateEntry(entry, savedPaths, state.selectedTagIds.toList())
                        committed = true
                    }
                    withContext(NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                        savedStateHandle.remove<String>("draft")
                        _uiState.update { it.copy(isSaving = false, isSaved = true, hasChanges = false) }
                    }
                } catch (e: CancellationException) {
                    if (!committed) copiedPaths.forEach { File(it).delete() }
                    throw e
                } catch (e: Exception) {
                    if (!committed) copiedPaths.forEach { File(it).delete() }
                    _uiState.update { it.copy(isSaving = false, saveError = "Не удалось сохранить запись. Попробуйте ещё раз") }
                }
            }
        }
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveError = null) }
    }

    fun searchLocationByName(query: String) {
        if (query.isBlank()) return
        findLocation { geocoderHelper.searchPlace(query.trim()) }
    }
}
