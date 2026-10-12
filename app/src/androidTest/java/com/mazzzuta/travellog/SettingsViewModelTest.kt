package com.mazzzuta.travellog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.utils.*
import com.mazzzuta.travellog.viewmodels.SettingsViewModel
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SettingsViewModelTest {
    @Test fun readCanRetryAndFailedWriteKeepsDialogOpen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var failRead = true
        val brokenStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (failRead) throw IOException("read failed")
                emit(emptyPreferences())
                awaitCancellation()
            }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw IOException("write failed")
            }
        }
        val store = ViewModelStore()
        try {
            val viewModel = withContext(Dispatchers.Main) {
                SettingsViewModel(UserPreferencesRepository(context, brokenStore), PhotoCleanup(context, database, PhotoStorageHelper(context)))
                    .also { store.put("settings", it) }
            }
            val failed = withTimeout(3000) { viewModel.state.first { !it.isLoading } }
            assertNotNull(failed.loadError)
            assertNull(failed.prefs)
            failRead = false
            withContext(Dispatchers.Main) { viewModel.load() }
            val loaded = withTimeout(3000) { viewModel.state.first { it.prefs != null } }
            assertNull(loaded.loadError)
            var closed = false
            withContext(Dispatchers.Main) { viewModel.setNickname("Новый ник") { closed = true } }
            val unsaved = withTimeout(3000) { viewModel.state.first { it.saveError != null && !it.isSaving } }
            assertEquals("Путешественник", unsaved.prefs!!.nickname)
            assertFalse(closed)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            database.close()
        }
    }
}
