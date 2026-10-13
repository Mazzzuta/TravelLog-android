package com.mazzzuta.travellog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mazzzuta.travellog.database.UserPrefs
import kotlinx.coroutines.CancellationException
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
            var retry by remember { mutableIntStateOf(0) }
            var readError by remember { mutableStateOf(false) }
            val prefs by produceState<UserPrefs?>(initialValue = null, retry) {
                readError = false
                try { prefsRepository.preferencesFlow.collect { value = it } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { readError = true }
            }
            val current = prefs ?: UserPrefs()

            val systemDark = isSystemInDarkTheme()
            val palette = Palettes.resolve(current, systemDark)

            CompositionLocalProvider(LocalDateFormat provides DateFormatOption.fromId(current.dateFormat)) {
                TravelLogTheme(palette = palette) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (readError) {
                            Text("Не удалось загрузить оформление и формат даты", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { retry++ }) { Text("Повторить") }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            // Ждём первого чтения настроек, чтобы при запуске не мелькали чужая палитра и формат даты.
                            if (prefs != null) TravelLogNavHost()
                            else if (!readError) CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
