package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.viewmodels.calculateStatistics
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class StatisticsTest {
    private val zone = ZoneId.of("Asia/Chita")

    @Test fun localMonthBoundariesAndRelatedDataAreCounted() {
        val tag = TagEntity(id = 1, name = "Горы")
        val first = EntryWithDetails(EntryEntity(id = 1, title = "A", description = "", date =
            Instant.parse("2025-12-31T16:00:00Z").toEpochMilli(), latitude = 0.0, longitude = 0.0),
            listOf(PhotoEntity(id = 1, entryId = 1, filePath = "a.jpg")), listOf(tag))
        val second = first.copy(entry = first.entry.copy(id = 2, date = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()),
            photos = emptyList(), tags = listOf(tag, tag))
        val stats = calculateStatistics(listOf(first, second), 3, zone)
        assertEquals(2, stats.entries)
        assertEquals(3, stats.trips)
        assertEquals(1, stats.photos)
        assertEquals(1, stats.places)
        assertEquals(listOf(1, 0, 1) + List(9) { 0 }, stats.monthsByYear[2026])
        assertFalse(stats.monthsByYear.containsKey(2025))
        assertEquals(listOf(TagCount("Горы", 2)), stats.tags)
    }

    @Test fun emptyDiaryAndUnknownLocationsHaveNoInventedData() {
        val empty = calculateStatistics(emptyList(), 0, zone)
        assertEquals(0, empty.entries)
        assertTrue(empty.monthsByYear.isEmpty())
        assertTrue(empty.tags.isEmpty())
        val entry = EntryWithDetails(EntryEntity(title = "A", description = "", date = 0), emptyList(), emptyList())
        assertEquals(0, calculateStatistics(listOf(entry), 0, zone).places)
    }
}
