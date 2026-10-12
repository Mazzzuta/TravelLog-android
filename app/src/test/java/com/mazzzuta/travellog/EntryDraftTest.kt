package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.viewmodels.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class EntryDraftTest {
    @Test fun oversizedDraftIsNotPlacedInAndroidSavedState() {
        assertNotNull(CreateEntryUiState(title = "Обычная запись").draft().encodeForSavedState())
        assertNull(CreateEntryUiState(description = "я".repeat(128 * 1024)).draft().encodeForSavedState())
    }

    @Test fun restoresAllEditableFieldsWithoutRestoringBusyOrErrorStates() {
        val edited = CreateEntryUiState(title = "Байкал", description = "Текст", date = 123, tripId = 7,
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
