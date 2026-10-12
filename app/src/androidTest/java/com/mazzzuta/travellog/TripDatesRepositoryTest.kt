package com.mazzzuta.travellog

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TripDatesRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var trips: TripRepository
    private lateinit var entries: EntryRepository
    private fun date(day: Int, hour: Int = 0) = LocalDate.of(2020, 10, day).atTime(hour, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun trip(start: Int = 10, end: Int = 12) = TripEntity(title = "Самара", startDate = date(start), endDate = date(end))
    private fun entry(day: Int, tripId: Long? = null) = EntryEntity(title = "Запись", description = "", date = date(day, 23), tripId = tripId)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        trips = TripRepository(database.tripDao(), database)
        entries = EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database)
    }
    @After fun tearDown() { database.close() }

    private suspend fun rejects(block: suspend () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        fail("Нарушение периода должно быть отклонено")
    }

    @Test fun overlappingCreationAndShrinkingTripAreRejectedWithoutChangingData() = runBlocking {
        val id = trips.createTrip(trip())
        rejects { trips.createTrip(trip(11, 13)) }
        val saved = trips.getAllTrips().first().single()
        assertEquals(id, saved.id)
        entries.createEntry(entry(12, id), emptyList(), emptyList())
        rejects { trips.updateTrip(saved.copy(endDate = date(11))) }
        assertEquals(saved, trips.getAllTrips().first().single())
    }

    @Test fun automaticAssignmentAndDateEditingUseCurrentTrips() = runBlocking {
        val first = trips.createTrip(trip())
        val second = trips.createTrip(trip(13, 14))
        val id = entries.createEntry(entry(12), emptyList(), emptyList(), autoAssignTrip = true)
        val before = entries.getEntryWithDetails(id).first()!!.entry
        assertEquals(first, before.tripId)
        entries.updateEntry(before.copy(date = date(13)), emptyList(), emptyList(), autoAssignTrip = true)
        assertEquals(second, entries.getEntryWithDetails(id).first()!!.entry.tripId)
        val standalone = entries.createEntry(entry(11), emptyList(), emptyList())
        assertNull(entries.getEntryWithDetails(standalone).first()!!.entry.tripId)
        val unmatched = entries.createEntry(entry(9), emptyList(), emptyList(), autoAssignTrip = true)
        assertNull(entries.getEntryWithDetails(unmatched).first()!!.entry.tripId)
    }

    @Test fun explicitOutOfRangeAssignmentCannotCreateOrModifyEntry() = runBlocking {
        val id = trips.createTrip(trip(13, 14))
        rejects { entries.createEntry(entry(12, id), listOf("photo.jpg"), emptyList()) }
        assertTrue(entries.searchEntries().first().isEmpty())
        val entryId = entries.createEntry(entry(13, id), listOf("keep.jpg"), emptyList())
        val before = entries.getEntryWithDetails(entryId).first()!!
        rejects { entries.updateEntry(before.entry.copy(date = date(12)), listOf("replace.jpg"), emptyList()) }
        assertEquals(before, entries.getEntryWithDetails(entryId).first())
    }

    @Test fun concurrentOverlappingCreationAllowsOnlyOneTrip() = runBlocking {
        val results = listOf(trip(), trip(11, 13)).map { value ->
            async(Dispatchers.IO) { runCatching { trips.createTrip(value) } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, trips.getAllTrips().first().size)
    }

    @Test fun futureEntryCannotBeCreatedOrReplaceExistingEntry() = runBlocking {
        val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val future = entry(10).copy(date = tomorrow)
        rejects { entries.createEntry(future, listOf("new.jpg"), emptyList()) }
        assertTrue(entries.searchEntries().first().isEmpty())
        val id = entries.createEntry(entry(10), listOf("keep.jpg"), emptyList())
        val before = entries.getEntryWithDetails(id).first()!!
        rejects { entries.updateEntry(before.entry.copy(date = tomorrow), listOf("replace.jpg"), emptyList()) }
        assertEquals(before, entries.getEntryWithDetails(id).first())
    }
}
