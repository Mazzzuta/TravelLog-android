package com.mazzzuta.travellog

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EntryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: EntryRepository
    private var entryId = 0L
    private var tagId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        repository = EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database)
        val tripId = database.tripDao().insert(TripEntity(title = "Поездка", startDate = 1000L))
        tagId = database.tagDao().insert(TagEntity(name = "Природа"))
        entryId = repository.createEntry(
            EntryEntity(tripId = tripId, title = "До", description = "Текст", date = 2000L,
                latitude = 1.0, longitude = 2.0, createdAt = 3000L),
            listOf("old.jpg"), listOf(tagId),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun editingReplacesPhotosAndTagsAndPreservesEntryIdentity() = runBlocking {
        val before = repository.getEntryWithDetails(entryId).first()!!.entry
        repository.updateEntry(before.copy(title = "После", description = "Новый текст", updatedAt = 4000L),
            listOf("new.jpg", "second.jpg"), emptyList())
        val after = repository.getEntryWithDetails(entryId).first()!!
        assertEquals(before.id, after.entry.id)
        assertEquals(before.tripId, after.entry.tripId)
        assertEquals(before.date, after.entry.date)
        assertEquals(before.createdAt, after.entry.createdAt)
        assertEquals("После", after.entry.title)
        assertEquals("Новый текст", after.entry.description)
        assertEquals(4000L, after.entry.updatedAt)
        assertEquals(listOf("new.jpg", "second.jpg"), after.photos.sortedBy { it.orderIndex }.map { it.filePath })
        assertTrue(after.tags.isEmpty())
        repository.updateEntry(after.entry, emptyList(), listOf(tagId))
        val withoutPhotos = repository.getEntryWithDetails(entryId).first()!!
        assertTrue(withoutPhotos.photos.isEmpty())
        assertEquals(listOf(tagId), withoutPhotos.tags.map { it.id })
    }

    @Test
    fun entryCanMoveToAnotherTripWithoutLosingPhotosOrTags() = runBlocking {
        val before = repository.getEntryWithDetails(entryId).first()!!
        val tripId = database.tripDao().insert(TripEntity(title = "Другая поездка", startDate = 1000L))
        repository.updateEntry(before.entry.copy(tripId = tripId), before.photos.map { it.filePath }, before.tags.map { it.id })
        val after = repository.getEntryWithDetails(entryId).first()!!
        assertEquals(tripId, after.entry.tripId)
        assertEquals(before.entry.id, after.entry.id)
        assertEquals(before.photos.map { it.filePath }, after.photos.map { it.filePath })
        assertEquals(before.tags, after.tags)
        assertTrue(database.tripDao().getTripWithEntries(before.entry.tripId!!).first().entries.isEmpty())
        assertEquals(entryId, database.tripDao().getTripWithEntries(tripId).first().entries.single().id)
    }

    @Test
    fun deletingTripCascadesOnlyItsEntriesPhotosAndTagLinks() = runBlocking {
        val before = repository.getEntryWithDetails(entryId).first()!!
        val trips = TripRepository(database.tripDao())
        val otherTripId = trips.createTrip(TripEntity(title = "Оставить", startDate = 1000L))
        val otherEntryId = repository.createEntry(before.entry.copy(id = 0L, tripId = otherTripId), listOf("keep.jpg"), listOf(tagId))
        val trip = trips.getAllTrips().first().first { it.id == before.entry.tripId }

        trips.deleteTrip(trip, deleteEntries = true)

        assertNull(repository.getEntryWithDetails(entryId).first())
        assertTrue(database.photoDao().getPhotosForEntry(entryId).isEmpty())
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM entry_tag_cross_ref WHERE entryId = $entryId"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        assertEquals(listOf(otherTripId), trips.getAllTrips().first().map { it.id })
        val remaining = repository.getEntryWithDetails(otherEntryId).first()!!
        assertEquals(listOf("keep.jpg"), remaining.photos.map { it.filePath })
        assertEquals(listOf(tagId), remaining.tags.map { it.id })
        assertEquals(listOf(tagId), repository.getAllTags().first().map { it.id })
    }

    @Test
    fun standaloneEntrySurvivesDeletingItsFormerTrip() = runBlocking {
        val before = repository.getEntryWithDetails(entryId).first()!!
        repository.updateEntry(before.entry.copy(tripId = null), before.photos.map { it.filePath }, before.tags.map { it.id })
        database.tripDao().delete(database.tripDao().getAllTrips().first().single())
        val after = repository.getEntryWithDetails(entryId).first()!!
        assertNull(after.entry.tripId)
        assertEquals(before.photos.map { it.filePath }, after.photos.map { it.filePath })
        assertEquals(before.tags, after.tags)
        val standaloneId = repository.createEntry(before.entry.copy(id = 0L, tripId = null), emptyList(), emptyList())
        assertNull(repository.getEntryWithDetails(standaloneId).first()!!.entry.tripId)
        assertTrue(database.tripDao().getAllTrips().first().isEmpty())
    }

    @Test
    fun deletingOnlyTripKeepsEntriesWithPhotosAndTags() = runBlocking {
        val before = repository.getEntryWithDetails(entryId).first()!!
        val trips = TripRepository(database.tripDao())
        val trip = trips.getAllTrips().first().single()
        val otherTripId = trips.createTrip(TripEntity(title = "Другая поездка", startDate = 1000L))
        val otherEntryId = repository.createEntry(before.entry.copy(id = 0L, tripId = otherTripId), emptyList(), emptyList())

        trips.deleteTrip(trip)

        val after = repository.getEntryWithDetails(entryId).first()!!
        assertNull(after.entry.tripId)
        assertEquals(before.entry.id, after.entry.id)
        assertEquals(before.entry.title, after.entry.title)
        assertEquals(before.entry.date, after.entry.date)
        assertEquals(before.photos, after.photos)
        assertEquals(before.tags, after.tags)
        assertEquals(otherTripId, repository.getEntryWithDetails(otherEntryId).first()!!.entry.tripId)
        assertEquals(listOf(otherTripId), trips.getAllTrips().first().map { it.id })
    }

    @Test
    fun searchTripAndDatesWorkTogetherWithInclusiveBoundaries() = runBlocking {
        val original = repository.getEntryWithDetails(entryId).first()!!.entry
        val startId = repository.createEntry(original.copy(id = 0L, title = "До A", date = 1000L), emptyList(), emptyList())
        val endId = repository.createEntry(original.copy(id = 0L, title = "До B", date = 3000L), emptyList(), emptyList())
        repository.createEntry(original.copy(id = 0L, date = 3001L), emptyList(), emptyList())
        repository.createEntry(original.copy(id = 0L, title = "Другое", description = "Другое"), emptyList(), emptyList())
        val otherTrip = database.tripDao().insert(TripEntity(title = "Другая", startDate = 1000L))
        repository.createEntry(original.copy(id = 0L, tripId = otherTrip), emptyList(), emptyList())
        val standalone = repository.createEntry(original.copy(id = 0L, tripId = null), emptyList(), emptyList())
        val filtered = repository.searchEntries(query = "До", tripId = original.tripId,
            dateFrom = 1000L, dateTo = 3000L, sortBy = "date_asc").first()
        assertEquals(listOf(startId, entryId, endId), filtered.map { it.entry.id })
        assertEquals(listOf(standalone), repository.searchEntries(query = "До", dateFrom = 1000L,
            dateTo = 3000L, onlyWithoutTrip = true).first().map { it.entry.id })
        assertEquals(listOf(endId, entryId, startId), repository.searchEntries(query = "До", tripId = original.tripId,
            dateFrom = 1000L, dateTo = 3000L).first().map { it.entry.id })
    }

    @Test
    fun invalidTagRollsBackEntireEdit() = runBlocking {
        val before = repository.getEntryWithDetails(entryId).first()!!
        try {
            repository.updateEntry(before.entry.copy(title = "Не сохранять"), listOf("new.jpg"), listOf(Long.MAX_VALUE))
            fail("Ожидалась ошибка внешнего ключа")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Ошибка тега должна откатить также название и фотографии.
        }
        assertEquals(before, repository.getEntryWithDetails(entryId).first())
    }
}
