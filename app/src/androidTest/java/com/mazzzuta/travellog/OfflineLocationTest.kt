package com.mazzzuta.travellog

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.utils.*
import com.mazzzuta.travellog.viewmodels.CreateEntryViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OfflineLocationTest {
    @Test fun offlineSearchShowsWarningStopsLoadingAndKeepsDraft() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val offline = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
        }
        assertFalse(offline.hasInternetConnection())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val viewModel = withContext(Dispatchers.Main) {
                CreateEntryViewModel(
                    EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database),
                    TripRepository(database.tripDao(), database), GeocoderHelper(offline),
                    LocationHelper(context), PhotoStorageHelper(context),
                ).also { store.put("entry", it) }
            }
            repeat(2) {
                withContext(Dispatchers.Main) {
                    viewModel.onTitleChanged("Несохранённая запись")
                    viewModel.searchLocationByName("Самара")
                }
                val state = viewModel.uiState.value
                assertFalse(state.isDetectingLocation)
                assertEquals(NoInternetException().message, state.locationSearchError)
                assertEquals("Несохранённая запись", state.title)
                assertNull(state.latitude)
                assertFalse(state.locationFound)
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            database.close()
        }
    }

    @Test fun blockedLegacyGeocoderDoesNotHoldLoadingAfterTimeout() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        try {
            val result = withTimeoutOrNull(1000) {
                awaitLegacyAddress {
                    started.countDown()
                    try { release.await(); null } finally { finished.countDown() }
                }
                "unexpected result"
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertNull(result)
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }
    }
}
