package com.mazzzuta.travellog

import android.os.Bundle
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.viewmodels.calculateStatistics
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StatisticsQueriesTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: EntryRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repository = EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database)
    }

    @After fun tearDown() { database.close() }

    @Test fun aggregatesKeepEmptyTripsUnusedTagsAndValidDistinctPlacesCorrect() = runBlocking {
        val dao = database.backupDao()
        dao.insertTrips(listOf(TripEntity(id = 1, title = "Пустая", startDate = 0)))
        dao.insertEntries(listOf(
            EntryEntity(id = 1, title = "Нулевая точка", description = "", date = 0, latitude = 0.0, longitude = 0.0),
            EntryEntity(id = 2, title = "Та же точка", description = "", date = 1, latitude = 0.0, longitude = 0.0),
            EntryEntity(id = 3, title = "Без места", description = "", date = 2),
            EntryEntity(id = 4, title = "Некорректное место", description = "", date = 3, latitude = 91.0, longitude = 1.0),
            EntryEntity(id = 5, title = "Неполная точка", description = "", date = 4, latitude = 1.0),
        ))
        dao.insertPhotos(listOf(PhotoEntity(id = 1, entryId = 1, filePath = "test.jpg")))
        dao.insertTags(listOf(TagEntity(id = 1, name = "Горы"), TagEntity(id = 2, name = "Не выбран")))
        dao.insertLinks(listOf(EntryTagCrossRef(1, 1), EntryTagCrossRef(2, 1)))
        val statistics = calculateStatistics(repository.observeStatistics().first(), ZoneId.of("UTC"))
        assertEquals(5, statistics.entries)
        assertEquals(1, statistics.trips)
        assertEquals(1, statistics.photos)
        assertEquals(1, statistics.places)
        assertEquals(listOf(TagCount("Горы", 2)), statistics.tags)
        assertEquals(listOf(1L, 2L), repository.getMapEntries().first().map { it.entry.id }.sorted())
        assertEquals(StatisticsTotals(0, 0, 0), Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build().let { empty ->
            try { empty.entryDao().getStatisticsTotals() } finally { empty.close() }
        })
    }

    @Test fun statisticsObservePhotoTripAndTagChanges() = runBlocking {
        val snapshots = mutableListOf<StatisticsData>()
        val observer = launch { repository.observeStatistics().collect { snapshots.add(it) } }
        try {
            withTimeout(5000) { while (snapshots.isEmpty()) delay(10) }
            database.withTransaction {
                val dao = database.backupDao()
                dao.insertTrips(listOf(TripEntity(id = 1, title = "Поездка", startDate = 0)))
                dao.insertEntries(listOf(EntryEntity(id = 1, title = "Запись", description = "", date = 0)))
                dao.insertPhotos(listOf(PhotoEntity(id = 1, entryId = 1, filePath = "test.jpg")))
                dao.insertTags(listOf(TagEntity(id = 1, name = "Тег")))
                dao.insertLinks(listOf(EntryTagCrossRef(1, 1)))
            }
            withTimeout(5000) { while (snapshots.last().totals.photos != 1) delay(10) }
            assertEquals(listOf(0L), snapshots.last().dates)
            assertEquals(1, snapshots.last().totals.trips)
            assertEquals(listOf(TagCount("Тег", 1)), snapshots.last().tags)
            database.backupDao().clearEntries()
            withTimeout(5000) { while (snapshots.last().dates.isNotEmpty()) delay(10) }
            assertEquals(0, snapshots.last().totals.photos)
            assertEquals(0, snapshots.last().tags.single().count)
        } finally { observer.cancelAndJoin() }
    }

    @Test fun largeDiaryComparesOldAndOptimizedQueries() = runBlocking {
        database.withTransaction {
            val dao = database.backupDao()
            dao.insertTrips((1L..100).map { TripEntity(id = it, title = "Поездка $it", startDate = 0) })
            dao.insertEntries((1L..1000).map { id ->
                EntryEntity(id = id, tripId = (id % 100) + 1, title = "Запись $id", description = "x".repeat(8192), date = id,
                    latitude = if (id % 4 == 0L) 10.0 else null, longitude = if (id % 4 == 0L) 20.0 else null)
            })
            dao.insertPhotos((1L..10000).map { PhotoEntity(id = it, entryId = (it % 1000) + 1, filePath = "photo-$it.jpg") })
            dao.insertTags((1L..3).map { TagEntity(id = it, name = "Тег $it") })
            dao.insertLinks((1L..1000).flatMap { entry -> (1L..3).map { EntryTagCrossRef(entry, it) } })
        }
        suspend fun oldStatistics() {
            val entries = repository.searchEntries().first()
            assertEquals(1000, entries.size)
            assertEquals(10000, entries.sumOf { it.photos.size })
            assertEquals(100, database.tripDao().getAllTrips().first().size)
        }
        suspend fun newStatistics() {
            val result = calculateStatistics(repository.observeStatistics().first(), ZoneId.of("UTC"))
            assertEquals(1000, result.entries)
            assertEquals(10000, result.photos)
            assertEquals(100, result.trips)
            assertEquals(1, result.places)
            assertEquals(List(3) { 1000 }, result.tags.map { it.count })
        }
        suspend fun measure(block: suspend () -> Unit): Double {
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        }
        oldStatistics(); newStatistics()
        val old = mutableListOf<Double>()
        val optimized = mutableListOf<Double>()
        val oldMap = mutableListOf<Double>()
        val optimizedMap = mutableListOf<Double>()
        repeat(7) {
            old.add(measure { oldStatistics() })
            optimized.add(measure { newStatistics() })
            oldMap.add(measure { assertEquals(250, repository.searchEntries().first().count { it.entry.hasCoordinates() }) })
            optimizedMap.add(measure { assertEquals(250, repository.getMapEntries().first().size) })
        }
        fun median(values: List<Double>) = values.sorted()[values.size / 2]
        val report = "1000 entries / 10000 photos / 3000 tag links; median of 7 warm reads (ms): " +
            "statistics before=${median(old)} after=${median(optimized)}; map before=${median(oldMap)} after=${median(optimizedMap)}\n"
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", report) })
    }
}
