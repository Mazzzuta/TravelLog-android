package com.mazzzuta.travellog

import android.app.Application
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TravelLogApp : Application() {

    private val prefsRepository: UserPreferencesRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TravelLogApp)
            modules(appModule)
        }
        CoroutineScope(Dispatchers.IO).launch { prefsRepository.ensureRegisteredAt() }
    }
}