package com.mazzzuta.travellog.viewmodels

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EntryDraft(
    val title: String, val description: String, val date: Long, val tripId: Long?,
    val photos: List<String>, val tags: Set<Long>, val newTag: String,
    val latitude: Double?, val longitude: Double?, val place: String?,
) {
    fun applyTo(state: CreateEntryUiState) = state.copy(title = title, description = description, date = date,
        tripId = tripId, photoUris = photos, selectedTagIds = tags, newTagName = newTag,
        latitude = latitude, longitude = longitude, placeName = place, hasChanges = true)

    // shortcut: SavedState имеет общий лимит Bundle; большие черновики перенести в файлы при необходимости.
    fun encodeForSavedState(): String? = Json.encodeToString(this).takeIf { it.toByteArray(Charsets.UTF_8).size <= 128 * 1024 }
}

internal fun CreateEntryUiState.draft() = EntryDraft(title, description, date, tripId, photoUris,
    selectedTagIds, newTagName, latitude, longitude, placeName)
