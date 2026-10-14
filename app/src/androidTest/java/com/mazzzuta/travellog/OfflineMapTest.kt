package com.mazzzuta.travellog

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import com.mazzzuta.travellog.ui.map.MapScreen
import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.ui.theme.TravelLogTheme
import com.mazzzuta.travellog.utils.LocationHelper
import com.mazzzuta.travellog.viewmodels.MapViewModel
import org.junit.Rule
import org.junit.Test

class OfflineMapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlineMapShowsWarningWithoutEndlessProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val offline = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            lateinit var viewModel: MapViewModel
            compose.runOnUiThread {
                viewModel = MapViewModel(
                    EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database),
                    LocationHelper(context),
                ).also { store.put("map", it) }
            }
            compose.setContent {
                val resultOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
                CompositionLocalProvider(LocalContext provides offline, LocalActivityResultRegistryOwner provides resultOwner) {
                    TravelLogTheme(Palettes.LightBase) {
                        MapScreen(onEntryClick = {}, viewModel = viewModel)
                    }
                }
            }
            compose.waitUntil(5000) { !viewModel.uiState.value.isLoading }
            compose.onNodeWithText("Нет подключения к интернету. Для загрузки карты подключитесь к сети.").assertIsDisplayed()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        } finally {
            compose.runOnUiThread { store.clear() }
            database.close()
        }
    }
}
