package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.EntryEntity
import com.mazzzuta.travellog.database.hasCoordinates
import org.junit.Assert.*
import org.junit.Test

class MapCoordinatesTest {
    @Test fun onlyValidCoordinatePairsAppearOnMap() {
        val entry = EntryEntity(title = "Место", description = "", date = 0)
        assertFalse(entry.hasCoordinates())
        assertFalse(entry.copy(latitude = 20.0).hasCoordinates())
        assertFalse(entry.copy(latitude = 91.0, longitude = 0.0).hasCoordinates())
        assertFalse(entry.copy(latitude = 0.0, longitude = Double.NaN).hasCoordinates())
        assertTrue(entry.copy(latitude = 0.0, longitude = 0.0).hasCoordinates())
        assertTrue(entry.copy(latitude = -90.0, longitude = 180.0).hasCoordinates())
    }
}
