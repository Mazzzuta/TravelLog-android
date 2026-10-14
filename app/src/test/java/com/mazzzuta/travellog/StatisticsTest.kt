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
        val data = StatisticsData(
            dates = listOf(Instant.parse("2025-12-31T16:00:00Z").toEpochMilli(),
                Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()),
            totals = StatisticsTotals(trips = 3, photos = 1, places = 1),
            tags = listOf(TagCount("Горы", 2), TagCount("Не используется", 0)),
        )
        val stats = calculateStatistics(data, zone)
        assertEquals(2, stats.entries)
        assertEquals(3, stats.trips)
        assertEquals(1, stats.photos)
        assertEquals(1, stats.places)
        assertEquals(listOf(1, 0, 1) + List(9) { 0 }, stats.monthsByYear[2026])
        assertFalse(stats.monthsByYear.containsKey(2025))
        assertEquals(listOf(TagCount("Горы", 2)), stats.tags)
    }

    @Test fun emptyDiaryAndUnknownLocationsHaveNoInventedData() {
        val empty = calculateStatistics(StatisticsData(emptyList(), StatisticsTotals(0, 0, 0), emptyList()), zone)
        assertEquals(0, empty.entries)
        assertTrue(empty.monthsByYear.isEmpty())
        assertTrue(empty.tags.isEmpty())
        val data = StatisticsData(listOf(0L), StatisticsTotals(0, 0, 0), emptyList())
        assertEquals(0, calculateStatistics(data, zone).places)
    }
}
