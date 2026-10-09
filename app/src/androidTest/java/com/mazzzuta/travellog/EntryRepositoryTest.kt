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
        assertTrue(database.tripDao().getTripWithEntries(before.entry.tripId).first().entries.isEmpty())
        assertEquals(entryId, database.tripDao().getTripWithEntries(tripId).first().entries.single().id)
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
