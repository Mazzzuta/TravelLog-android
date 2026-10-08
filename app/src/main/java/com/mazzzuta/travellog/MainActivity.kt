package com.mazzzuta.travellog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mazzzuta.travellog.database.ThemeMode
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.navigation.TravelLogNavHost
import com.mazzzuta.travellog.ui.theme.Palettes
import com.mazzzuta.travellog.ui.theme.TravelLogTheme
import com.mazzzuta.travellog.utils.DateFormatOption
import com.mazzzuta.travellog.utils.LocalDateFormat
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val prefsRepository: UserPreferencesRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // null, пока настройки не прочитаны: так не мелькает светлая тема у тех, кто выбрал тёмную
            val prefs by prefsRepository.preferencesFlow.collectAsState(initial = null)
            val current = prefs ?: return@setContent

            val systemDark = isSystemInDarkTheme()
            val useDark = when (current.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            val palette = Palettes.resolve(useDark, if (useDark) current.darkSchemeId else current.lightSchemeId)

            CompositionLocalProvider(LocalDateFormat provides DateFormatOption.fromId(current.dateFormat)) {
                TravelLogTheme(palette = palette) {
                    TravelLogNavHost()
                }
            }
        }
    }
}