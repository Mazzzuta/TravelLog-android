package com.mazzzuta.travellog

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.backup.*
import com.mazzzuta.travellog.database.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BackupManagerTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var manager: BackupManager
    private lateinit var scope: CoroutineScope
    private lateinit var storage: com.mazzzuta.travellog.utils.PhotoStorageHelper

    @Before fun setUp() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(root, "test.preferences_pb") }
        preferences = UserPreferencesRepository(context, store)
        storage = com.mazzzuta.travellog.utils.PhotoStorageHelper(context)
        manager = BackupManager(context, database, preferences, storage)
        database.backupDao().insertEntries(listOf(EntryEntity(id = 9, title = "До импорта", description = "", date = 1)))
    }

    @After fun tearDown(): Unit = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        database.close()
        root.deleteRecursively()
    }

    @Test fun exportRestorePreservesDataSettingsAndCopiesPhotos() = runBlocking {
        val photo = File(context.filesDir, "photos/source.jpg").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val dao = database.backupDao()
        dao.insertTrips(listOf(TripEntity(id = 7, title = "Байкал", startDate = 1, coverPhotoPath = photo.path)))
        dao.insertEntries(listOf(EntryEntity(id = 10, tripId = 7, title = "Ольхон", description = "Текст", date = 2)))
        dao.insertPhotos(listOf(PhotoEntity(id = 21, entryId = 10, filePath = photo.path, orderIndex = 3)))
        dao.insertTags(listOf(TagEntity(id = 31, name = "Природа")))
        dao.insertLinks(listOf(EntryTagCrossRef(10, 31)))
        val prefs = UserPrefs(nickname = "Тест", registeredAt = 123, themeMode = ThemeMode.DARK, schemeId = "neon")
        preferences.restore(prefs)
        val before = dao.entries()
        val archive = File(root, "diary.zip")
        manager.export(Uri.fromFile(archive))
        dao.clearEntries()
        dao.insertEntries(listOf(EntryEntity(id = 99, title = "Заменить", description = "", date = 1)))
        preferences.setNickname("Заменить")

        val prepared = manager.prepareImport(Uri.fromFile(archive))
        try {
            assertEquals(99L, dao.entries().single().id) // Проверка архива ещё не меняет дневник.
            assertTrue(manager.restore(prepared))
        } finally { prepared.discard() }

        assertEquals(before, dao.entries())
        assertEquals(prefs, preferences.preferencesFlow.first())
        val restored = dao.photos().single()
        assertEquals(21L, restored.id)
        assertEquals(3, restored.orderIndex)
        assertNotEquals(photo.path, restored.filePath)
        assertArrayEquals(photo.readBytes(), File(restored.filePath).readBytes())
        assertEquals(restored.filePath, dao.trips().single().coverPhotoPath)
        assertEquals(listOf(EntryTagCrossRef(10, 31)), dao.links())
    }

    @Test fun failedDatabaseImportRollsBackExistingDiary() = runBlocking {
        val dao = database.backupDao()
        val before = dao.entries()
        val invalid = BackupData(trips = emptyList(), entries = listOf(before.single().copy(tripId = 999)),
            photos = emptyList(), tags = emptyList(), links = emptyList(), preferences = UserPrefs())
        val prepared = PreparedImport(invalid, File(context.cacheDir, "staging").apply { mkdirs() })
        try {
            try {
                manager.restore(prepared)
                fail("Нарушение внешнего ключа должно откатить транзакцию")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(before, dao.entries())
            assertTrue(File(context.filesDir, "photos").listFiles().orEmpty().isEmpty())
        } finally { prepared.discard() }
    }

    @Test fun cleanupWaitsForPhotoCommitAndRemovesItOnlyAfterDeletion() = runBlocking {
        val cleanup = com.mazzzuta.travellog.utils.PhotoCleanup(context, database, storage)
        val copied = CompletableDeferred<Unit>()
        val commit = CompletableDeferred<Unit>()
        val photo = File(context.filesDir, "photos/pending.jpg")
        val writer = launch {
            storage.withPhotoAccess {
                photo.parentFile!!.mkdirs()
                photo.writeText("photo")
                copied.complete(Unit)
                commit.await()
                database.backupDao().insertPhotos(listOf(PhotoEntity(id = 1, entryId = 9, filePath = photo.path)))
            }
        }
        withTimeout(3000) { copied.await() }
        val cleaning = async(start = CoroutineStart.UNDISPATCHED) { cleanup.clean() }
        assertFalse(cleaning.isCompleted)
        commit.complete(Unit)
        writer.join()
        assertEquals(0, withTimeout(3000) { cleaning.await() }.deleted)
        assertTrue(photo.exists())
        database.backupDao().insertTrips(listOf(TripEntity(id = 1, title = "Обложка", startDate = 1, coverPhotoPath = photo.path)))
        database.backupDao().clearEntries()
        assertEquals(0, cleanup.clean().deleted)
        assertTrue(photo.exists())
        database.backupDao().clearTrips()
        assertEquals(1, cleanup.clean().deleted)
        assertFalse(photo.exists())
    }
}
