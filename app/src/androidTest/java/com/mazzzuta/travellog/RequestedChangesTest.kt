package com.mazzzuta.travellog

import android.content.Context
import android.content.ContextWrapper
import android.location.Address
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.ui.createentry.CreateEntryScreen
import com.mazzzuta.travellog.ui.feed.EntryCard
import com.mazzzuta.travellog.ui.feed.FeedScreen
import com.mazzzuta.travellog.ui.settings.SettingsScreen
import com.mazzzuta.travellog.utils.*
import com.mazzzuta.travellog.viewmodels.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RequestedChangesTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private lateinit var entries: EntryRepository
    private lateinit var preferences: UserPreferencesRepository
    private val store = ViewModelStore()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        entries = EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database)
        val values = MutableStateFlow<Preferences>(emptyPreferences())
        val memoryStore = object : DataStore<Preferences> {
            override val data = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(values.value).also { values.value = it }
        }
        val safeContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ACTIVITY_SERVICE) null else super.getSystemService(name)
        }
        preferences = UserPreferencesRepository(safeContext, memoryStore)
    }

    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        database.close()
    }

    private fun <T : ViewModel> keep(create: () -> T): T {
        lateinit var result: T
        compose.runOnUiThread { result = create(); store.put("viewModel", result) }
        return result
    }

    @Test fun tagLengthAndBatchDeletionPreserveEntriesAndPhotos(): Unit = runBlocking {
        val first = entries.createTag("Первый")
        val second = entries.createTag("Второй")
        val keep = entries.createTag("Оставить")
        entries.createTag("x".repeat(MAX_TAG_LENGTH))
        try { entries.createTag("x".repeat(MAX_TAG_LENGTH + 1)); fail("Long tag accepted") }
        catch (_: IllegalArgumentException) { }
        val id = entries.createEntry(EntryEntity(title = "Запись", description = "Текст", date = 0), listOf("photo.jpg"), listOf(first, second, keep))
        entries.deleteTags(listOf(first, second))
        val result = entries.getEntryWithDetails(id).first()!!
        assertEquals("Запись", result.entry.title)
        assertEquals(listOf("photo.jpg"), result.photos.map { it.filePath })
        assertEquals(listOf(keep), result.tags.map { it.id })
        assertEquals(listOf(EntryTagCrossRef(id, keep)), database.backupDao().links())
        val many = (100L..1100L).map { TagEntity(it, "Тег $it") }
        database.backupDao().insertTags(many)
        entries.deleteTags(many.map { it.id })
        assertTrue(entries.getAllTags().first().none { it.id >= 100 })
    }

    @Test fun placeLabelsKeepLandmarksAddressesAndAvoidRepeatedCity() {
        val address = Address(Locale("ru")).apply {
            locality = "Санкт-Петербург"; countryName = "Россия"; featureName = "Газпром арена"
        }
        assertEquals("Газпром арена, Санкт-Петербург, Россия", formatPlaceName(address, "Газпром арена"))
        assertEquals("Газпром арена, Санкт-Петербург, Россия", formatPlaceName(address))
        address.apply { locality = "Чита"; thoroughfare = "ул. Пушкина"; subThoroughfare = "17"; featureName = "17" }
        assertEquals("ул. Пушкина 17, Чита, Россия", formatPlaceName(address, "ул. Пушкина 17"))
        assertEquals("ул. Пушкина 17, Чита, Россия", formatPlaceName(address))
        assertEquals("Чита, Россия", formatPlaceName(address, "Чита, Россия"))
    }

    @Test fun feedTypingKeepsLoadedContentAndFiltersStayInMenu(): Unit = runBlocking {
        entries.createEntry(EntryEntity(title = "Самара", description = "Текст", date = 0), emptyList(), emptyList())
        val viewModel = keep { FeedViewModel(entries, preferences, TripRepository(database.tripDao(), database)) }
        compose.setContent { MaterialTheme { FeedScreen({}, {}, {}, viewModel) } }
        compose.waitUntil(5000) { !viewModel.uiState.value.isLoading }
        compose.onNodeWithText("Самара").assertIsDisplayed()
        compose.onNodeWithText("Без поездки").assertDoesNotExist()
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Без поездки").performClick()
        compose.waitUntil(5000) { viewModel.uiState.value.onlyWithoutTrip }
        compose.onNodeWithText("Фильтры · 1").assertIsDisplayed()
        val seen = java.util.concurrent.CopyOnWriteArrayList<FeedUiState>()
        val collector = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { seen.add(it) } }
        try {
            for (character in "нет совпадений") {
                compose.onNode(hasSetTextAction()).performTextInput(character.toString())
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            }
            compose.waitUntil(5000) { viewModel.uiState.value.entries.isEmpty() }
            assertEquals("нет совпадений", viewModel.uiState.value.query)
            assertTrue(seen.none { it.isLoading })
        } finally { collector.cancelAndJoin() }
    }

    @Test fun changingSortStartsAtTopOfNewOrder(): Unit = runBlocking {
        repeat(30) { index ->
            entries.createEntry(EntryEntity(title = "Запись %02d".format(index + 1), description = "", date = index.toLong()),
                emptyList(), emptyList())
        }
        val viewModel = keep { FeedViewModel(entries, preferences, TripRepository(database.tripDao(), database)) }
        compose.setContent { MaterialTheme { FeedScreen({}, {}, {}, viewModel) } }
        compose.waitUntil(5000) { !viewModel.uiState.value.isLoading }
        compose.onNodeWithText("Запись 30").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(15)
        compose.onNodeWithText("Запись 30").assertDoesNotExist()
        compose.onNodeWithText("Сначала новые").performClick()
        compose.onNodeWithText("Сначала старые").performClick()
        compose.waitUntil(5000) { viewModel.uiState.value.sortOption == SortOption.DATE_ASC }
        compose.onNodeWithText("Запись 01").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(20)
        compose.onNodeWithText("Сначала старые").performClick()
        compose.onNodeWithText("Сначала новые").performClick()
        compose.waitUntil(5000) { viewModel.uiState.value.sortOption == SortOption.DATE_DESC }
        compose.onNodeWithText("Запись 30").assertIsDisplayed()
    }

    @Test fun feedCardFitsThreeLongTagsAndOverflowCount() {
        val tags = (1L..7).map { TagEntity(it, "Длинный тег $it ".repeat(5)) }
        val data = EntryWithDetails(EntryEntity(title = "Запись", description = "", date = 0), emptyList(), tags)
        compose.setContent { MaterialTheme { Box(Modifier.width(240.dp)) { EntryCard(data) {} } } }
        compose.onNodeWithText("+4").assertIsDisplayed()
        tags.take(3).forEach { compose.onNodeWithText(it.name).assertIsDisplayed() }
        tags.drop(3).forEach { compose.onNodeWithText(it.name).assertDoesNotExist() }
        val countBounds = compose.onNodeWithText("+4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        tags.take(3).forEach {
            val bounds = compose.onNodeWithText(it.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Tag bounds: $bounds; count bounds: $countBounds", bounds.right <= countBounds.left)
            assertEquals(countBounds.top, bounds.top, 1f)
        }
    }

    @Test fun longPressTagRequiresConfirmationAndRemovesSelection(): Unit = runBlocking {
        val id = entries.createTag("Лес")
        val viewModel = keep { CreateEntryViewModel(entries, TripRepository(database.tripDao(), database),
            GeocoderHelper(context), LocationHelper(context), PhotoStorageHelper(context)) }
        compose.setContent { MaterialTheme { CreateEntryScreen({}, {}, viewModel = viewModel) } }
        compose.waitUntil(5000) { viewModel.uiState.value.availableTags.any { it.id == id } }
        compose.runOnUiThread { viewModel.onTitleChanged("Черновик"); viewModel.toggleTag(id) }
        compose.onNodeWithText("Лес").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Удалить тег?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(1, entries.getAllTags().first().size)
        compose.onNodeWithText("Лес").performTouchInput { longClick() }
        compose.onNodeWithText("Удалить").performClick()
        compose.waitUntil(5000) { viewModel.uiState.value.availableTags.isEmpty() && !viewModel.uiState.value.isDeletingTag }
        assertTrue(viewModel.uiState.value.selectedTagIds.isEmpty())
        assertEquals("Черновик", viewModel.uiState.value.title)
    }

    @Test fun settingsBatchDeleteVersionAndSafeResetFailure(): Unit = runBlocking {
        val first = entries.createTag("Первый")
        val second = entries.createTag("Второй")
        val last = entries.createTag("Оставить")
        val entry = entries.createEntry(EntryEntity(title = "Сохранить", description = "", date = 0), emptyList(), listOf(first, second, last))
        val viewModel = keep { SettingsViewModel(preferences, PhotoCleanup(context, database, PhotoStorageHelper(context)), entries) }
        compose.setContent { MaterialTheme { SettingsScreen(viewModel) } }
        compose.waitUntil(5000) { viewModel.state.value.prefs != null }
        compose.onNodeWithText("Управление тегами").performScrollTo().performClick()
        compose.waitUntil(5000) { viewModel.state.value.tags.size == 3 }
        compose.onNodeWithText("Первый").performClick()
        compose.onNodeWithText("Второй").performClick()
        compose.onNodeWithText("Удалить выбранные (2)").performClick()
        assertEquals(3, entries.getAllTags().first().size)
        compose.onNodeWithText("Удалить").performClick()
        compose.waitUntil(5000) { viewModel.state.value.tags.size == 1 && !viewModel.state.value.isDeletingTags }
        compose.onNodeWithText("Закрыть").performClick()
        compose.onNodeWithText("Версия").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.9.0").assertIsDisplayed()
        compose.onNodeWithText("Удалить все данные").performScrollTo().performClick()
        compose.onNodeWithText("Удалить все данные?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        assertNotNull(entries.getEntryWithDetails(entry).first())
        compose.onNodeWithText("Удалить все данные").performClick()
        compose.onNodeWithText("Удалить всё").performClick()
        compose.waitUntil(5000) { viewModel.state.value.resetError != null && !viewModel.state.value.isResetting }
        compose.onNodeWithText("Не удалось удалить данные. Попробуйте ещё раз").assertIsDisplayed()
        assertNotNull(entries.getEntryWithDetails(entry).first())
        compose.onNodeWithText("Отмена").performClick()
    }
}
