package com.mazzzuta.travellog

import android.app.Application
import com.mazzzuta.travellog.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TravelLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TravelLogApp)
            modules(appModule)
        }
    }
}