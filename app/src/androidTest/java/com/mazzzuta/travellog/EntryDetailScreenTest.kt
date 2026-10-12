package com.mazzzuta.travellog

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.ui.entrydetail.EntryDetailScreen
import com.mazzzuta.travellog.viewmodels.EntryDetailViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EntryDetailScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var repository: EntryRepository
    private val store = ViewModelStore()
    private lateinit var viewModel: EntryDetailViewModel

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repository = EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database)
    }

    @After fun tearDown() {
        compose.runOnIdle { store.clear() }
        database.close()
    }

    private fun open(id: Long, onMapClick: () -> Unit = {}) {
        compose.runOnIdle {
            viewModel = EntryDetailViewModel(id, repository, TripRepository(database.tripDao(), database))
            store.put("detail", viewModel)
        }
        compose.setContent { EntryDetailScreen(id, onBack = {}, onEdit = {}, onMapClick = onMapClick, viewModel = viewModel) }
        compose.waitUntil(5000) { !viewModel.uiState.value.isLoading }
    }

    @Test fun missingEntryShowsMessageAndBackAction() {
        open(999)
        compose.onNodeWithText("Запись не найдена").assertIsDisplayed()
        compose.onNodeWithText("Назад").assertIsDisplayed()
    }

    @Test fun longEntryCanScrollToActionsAndShowsItsTrip() = runBlocking {
        val trip = database.tripDao().insert(TripEntity(title = "Байкал", startDate = 0))
        val id = repository.createEntry(EntryEntity(tripId = trip, title = "День у озера",
            description = "Длинное описание\n".repeat(100), date = 0), emptyList(), emptyList())
        open(id)
        compose.onNodeWithText("Поездка: Байкал").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Изменить").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("На карте").assertDoesNotExist()
    }

    @Test fun knownCoordinatesOfferMapActionEvenWithoutPlaceName() = runBlocking {
        val id = repository.createEntry(EntryEntity(title = "Точка", description = "", date = 0,
            latitude = 0.0, longitude = 0.0), emptyList(), emptyList())
        var opened = false
        open(id, onMapClick = { opened = true })
        compose.onNodeWithText("На карте").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun failedDeleteKeepsEntryAndAllowsRetry() = runBlocking {
        val id = repository.createEntry(EntryEntity(title = "Запись", description = "", date = 0), emptyList(), emptyList())
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER block_delete BEFORE DELETE ON entries BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        open(id)
        compose.onNodeWithContentDescription("Удалить").performScrollTo().performClick()
        compose.onNodeWithText("Удалить").performClick()
        compose.waitUntil(5000) { viewModel.uiState.value.deleteError != null }
        compose.onNodeWithText("Не удалось удалить запись. Попробуйте ещё раз").assertIsDisplayed()
        assertNotNull(repository.getEntryWithDetails(id).first())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER block_delete")
        compose.runOnIdle {
            viewModel.onDeleteConfirm()
            viewModel.onDeleteConfirm()
        }
        compose.waitUntil(5000) { viewModel.uiState.value.isDeleted }
        assertNull(repository.getEntryWithDetails(id).first())
    }
}
