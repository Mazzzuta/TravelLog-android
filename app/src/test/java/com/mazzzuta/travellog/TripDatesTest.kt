package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TripDatesTest {
    private val zone = ZoneId.of("Asia/Chita")
    private fun date(day: Int, hour: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun trip(id: Long = 1, start: Int = 10, end: Int? = 12) =
        TripEntity(id = id, title = "Самара", startDate = date(start), endDate = end?.let { date(it) })
    private fun entry(day: Int, tripId: Long? = null, hour: Int = 0) =
        EntryEntity(title = "Запись", description = "", date = date(day, hour), tripId = tripId)
    private fun rejects(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        fail("Нарушение периода должно быть отклонено")
    }

    @Test fun intersectionAndSharedBoundaryAreRejectedButNextDayIsAllowed() {
        rejects { validateTripChange(trip(id = 2, start = 11, end = 13), listOf(trip()), zone = zone) }
        rejects { validateTripChange(trip(id = 2, start = 12, end = 14), listOf(trip()), zone = zone) }
        rejects { validateTripChange(trip(id = 2, start = 9, end = 14), listOf(trip()), zone = zone) }
        validateTripChange(trip(id = 2, start = 13, end = 14), listOf(trip()), zone = zone)
        validateTripChange(trip(id = 2, start = 8, end = 9), listOf(trip()), zone = zone)
    }

    @Test fun ongoingTripBlocksLaterPeriodsButAllowsEarlierOnes() {
        rejects { validateTripChange(trip(id = 2, start = 13, end = 14), listOf(trip(end = null)), zone = zone) }
        validateTripChange(trip(id = 2, start = 8, end = 9), listOf(trip(end = null)), zone = zone)
        assertTrue(trip(end = null).containsDate(date(31), zone))
    }

    @Test fun dateBoundariesIncludeWholeFirstAndLastDays() {
        assertTrue(trip().containsDate(date(10), zone))
        assertTrue(trip().containsDate(date(12, 23), zone))
        assertFalse(trip().containsDate(date(9, 23), zone))
        assertFalse(trip().containsDate(date(13), zone))
        assertTrue(trip(end = 10).containsDate(date(10, 23), zone))
    }

    @Test fun editingIgnoresItselfButCannotExcludeExistingEntries() {
        validateTripChange(trip(), listOf(trip()), listOf(entry(12, 1, 23)), zone)
        rejects { validateTripChange(trip(end = 11), listOf(trip()), listOf(entry(12, 1)), zone) }
        rejects { validateTripChange(trip(start = 11), listOf(trip()), listOf(entry(10, 1)), zone) }
    }

    @Test fun automaticAssignmentMatchesPeriodAndLeavesUnmatchedEntryStandalone() {
        val trips = listOf(trip(), trip(id = 2, start = 13, end = 14))
        assertEquals(1L, resolveEntryTrip(entry(10), trips, true, zone).tripId)
        assertEquals(1L, resolveEntryTrip(entry(12, hour = 23), trips, true, zone).tripId)
        assertEquals(2L, resolveEntryTrip(entry(13, tripId = 1), trips, true, zone).tripId)
        assertNull(resolveEntryTrip(entry(9), trips, true, zone).tripId)
        assertNull(resolveEntryTrip(entry(11), trips, false, zone).tripId)
    }

    @Test fun invalidExplicitAssignmentAndAmbiguousLegacyPeriodsAreRejected() {
        rejects { resolveEntryTrip(entry(12, 1), listOf(trip(start = 13, end = 14)), false, zone) }
        rejects { resolveEntryTrip(entry(12, 99), listOf(trip()), false, zone) }
        rejects { resolveEntryTrip(entry(11), listOf(trip(), trip(id = 2, start = 11, end = 13)), true, zone) }
    }
}
