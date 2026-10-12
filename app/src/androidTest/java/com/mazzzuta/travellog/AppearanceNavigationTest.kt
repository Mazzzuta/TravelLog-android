package com.mazzzuta.travellog

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.di.appModule
import com.mazzzuta.travellog.navigation.TravelLogNavHost
import com.mazzzuta.travellog.ui.theme.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AppearanceNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var testKoin: KoinApplication
    private val schemeKey = stringPreferencesKey("scheme")
    private val values = MutableStateFlow<Preferences>(preferencesOf(schemeKey to Palettes.Force.id))
    private var pendingWrite: CompletableDeferred<Unit>? = null

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = object : DataStore<Preferences> {
            override val data = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                pendingWrite?.await()
                return transform(values.value).also { values.value = it }
            }
        }
        val repository = UserPreferencesRepository(context, store)
        testKoin = koinApplication {
            androidContext(context)
            modules(appModule, module {
                single { database }
                single { repository }
            })
        }
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                val prefs by repository.preferencesFlow.collectAsState(UserPrefs(schemeId = Palettes.Force.id))
                TravelLogTheme(Palettes.resolve(prefs, false)) { TravelLogNavHost() }
            }
        }
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText(Palettes.Berries.name).performScrollTo()
        Thread.sleep(300)
    }

    @After fun tearDown() {
        pendingWrite?.complete(Unit)
        testKoin.close()
        database.close()
    }

    @Test fun selectionAndSavingKeepTileBoundsStable() {
        val berries = compose.onNodeWithText(Palettes.Berries.name)
        val force = compose.onNodeWithText(Palettes.Force.name)
        val before = berries.fetchSemanticsNode().boundsInRoot
        val neighbour = force.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { pendingWrite = CompletableDeferred() }
        berries.performClick()
        assertEquals(before, berries.fetchSemanticsNode().boundsInRoot)
        assertEquals(neighbour, force.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { pendingWrite!!.complete(Unit) }
        compose.waitUntil { values.value[schemeKey] == Palettes.Berries.id }
        berries.assertIsSelected()
        assertEquals(before, berries.fetchSemanticsNode().boundsInRoot)
        assertEquals(neighbour, force.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun leavingDuringSaveDoesNotAllowFeedTouchesToChangeScheme() {
        compose.runOnIdle { pendingWrite = CompletableDeferred() }
        compose.onNodeWithText(Palettes.Berries.name).performClick()
        compose.onNodeWithText("Лента").performClick()
        compose.onNodeWithText("Мои записи").assertIsDisplayed()
        compose.onNodeWithText("ЦВЕТОВАЯ СХЕМА").assertDoesNotExist()
        compose.onNodeWithText("TRAVEL LOG").assertDoesNotExist()
        compose.onNodeWithText("Поездки").performClick()
        compose.onNodeWithText("Мои поездки").assertIsDisplayed()
        compose.runOnIdle { pendingWrite!!.complete(Unit) }
        compose.waitUntil { values.value[schemeKey] == Palettes.Berries.id }
        assertEquals(Palettes.Berries.id, values.value[schemeKey])
    }

    @Test fun rapidTabTapsOpenOnlyFirstDestinationAndLaterTapWorks() {
        val feed = compose.onNodeWithText("Лента").fetchSemanticsNode().boundsInRoot.center
        val statistics = compose.onNodeWithText("Статистика").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            click(feed)
            click(statistics)
        }
        compose.onNodeWithText("Мои записи").assertIsDisplayed()
        compose.onNodeWithText("ЦВЕТОВАЯ СХЕМА").assertDoesNotExist()
        Thread.sleep(300)
        compose.onNodeWithText("Статистика").performClick()
        compose.onNodeWithText("За всё время").assertIsDisplayed()
        compose.onNodeWithText("Мои записи").assertDoesNotExist()
    }
}
