package com.mazzzuta.travellog.viewmodels

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class EntryDraft(
    val title: String, val description: String, val date: Long, val autoAssignTrip: Boolean,
    val photos: List<String>, val tags: Set<Long>, val newTag: String,
    val latitude: Double?, val longitude: Double?, val place: String?,
) {
    fun applyTo(state: CreateEntryUiState) = state.copy(title = title, description = description, date = date,
        autoAssignTrip = autoAssignTrip, photoUris = photos, selectedTagIds = tags, newTagName = newTag,
        latitude = latitude, longitude = longitude, placeName = place, hasChanges = true)

    // shortcut: SavedState имеет общий лимит Bundle; большие черновики перенести в файлы при необходимости.
    fun encodeForSavedState(): String? = Json.encodeToString(this).takeIf { it.toByteArray(Charsets.UTF_8).size <= 128 * 1024 }
}

internal fun CreateEntryUiState.draft() = EntryDraft(title, description, date, autoAssignTrip, photoUris,
    selectedTagIds, newTagName, latitude, longitude, placeName)

internal fun decodeEntryDraft(encoded: String): EntryDraft {
    val fields = Json.parseToJsonElement(encoded).jsonObject
    // Сохраняем ввод из черновиков с прежним ручным выбором поездки.
    val compatible = if ("autoAssignTrip" in fields) fields else JsonObject(
        fields - "tripId" + ("autoAssignTrip" to JsonPrimitive(fields["tripId"]?.let { it != JsonNull } ?: false)))
    return Json.decodeFromJsonElement<EntryDraft>(compatible)
}
