package com.mazzzuta.travellog

import android.app.Application
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log
import kotlinx.coroutines.flow.retryWhen
import com.mazzzuta.travellog.database.AppDatabase
import com.mazzzuta.travellog.utils.PhotoCleanup
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TravelLogApp : Application() {

    private val prefsRepository: UserPreferencesRepository by inject()
    private val database: AppDatabase by inject()
    private val photoCleanup: PhotoCleanup by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TravelLogApp)
            modules(appModule)
        }
        val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try { prefsRepository.ensureRegisteredAt() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("TravelLog", "Не удалось сохранить дату первого запуска", e) }
        }
        scope.launch {
            database.invalidationTracker.createFlow("photos", "trips")
                .retryWhen { error, _ -> Log.w("TravelLog", "Не удалось прочитать ссылки фотографий", error); kotlinx.coroutines.delay(5000); true }
                .collect {
                    try { photoCleanup.clean() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.w("TravelLog", "Не удалось очистить фотографии", e) }
                }
        }
    }
}
