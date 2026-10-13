package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.viewmodels.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EntryDraftTest {
    @Test fun olderDraftKeepsInputAndConvertsManualTripChoice() {
        val draft = CreateEntryUiState(title = "Несохранённый текст", selectedTagIds = setOf(7)).draft()
        val fields = Json.parseToJsonElement(Json.encodeToString(draft)).jsonObject - "autoAssignTrip"
        val assigned = decodeEntryDraft(JsonObject(fields + ("tripId" to JsonPrimitive(9))).toString())
        val standalone = decodeEntryDraft(JsonObject(fields + ("tripId" to JsonNull)).toString())
        assertTrue(assigned.autoAssignTrip)
        assertFalse(standalone.autoAssignTrip)
        assertEquals(draft.title, assigned.title)
        assertEquals(draft.tags, standalone.tags)
    }

    @Test fun oversizedDraftIsNotPlacedInAndroidSavedState() {
        assertNotNull(CreateEntryUiState(title = "Обычная запись").draft().encodeForSavedState())
        assertNull(CreateEntryUiState(description = "я".repeat(128 * 1024)).draft().encodeForSavedState())
    }

    @Test fun restoresAllEditableFieldsWithoutRestoringBusyOrErrorStates() {
        val edited = CreateEntryUiState(title = "Байкал", description = "Текст", date = 123, autoAssignTrip = false,
            photoUris = listOf("content://photo/1", "/files/photos/2.jpg"), selectedTagIds = setOf(1, 2),
            newTagName = "Природа", latitude = 0.0, longitude = 0.0, placeName = "Место",
            isSaving = true, saveError = "Ошибка")
        val encoded = Json.encodeToString(edited.draft())
        val loaded = CreateEntryUiState(availableTrips = listOf(TripEntity(id = 7, title = "Поездка", startDate = 1)))
        val restored = Json.decodeFromString<EntryDraft>(encoded).applyTo(loaded)
        assertEquals(edited.draft(), restored.draft())
        assertTrue(restored.hasChanges)
        assertFalse(restored.isSaving)
        assertNull(restored.saveError)
        assertEquals(loaded.availableTrips, restored.availableTrips)
    }
}
