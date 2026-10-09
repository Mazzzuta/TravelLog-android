package com.mazzzuta.travellog.di

import androidx.room.Room
import com.mazzzuta.travellog.database.AppDatabase
import com.mazzzuta.travellog.database.MIGRATION_1_2
import com.mazzzuta.travellog.database.MIGRATION_2_3
import com.mazzzuta.travellog.viewmodels.MapViewModel
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.TripRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import com.mazzzuta.travellog.viewmodels.FeedViewModel
import org.koin.androidx.viewmodel.dsl.viewModel

import com.mazzzuta.travellog.utils.GeocoderHelper
import com.mazzzuta.travellog.utils.LocationHelper
import com.mazzzuta.travellog.utils.PhotoStorageHelper
import com.mazzzuta.travellog.viewmodels.CreateEntryViewModel
import com.mazzzuta.travellog.viewmodels.EntryDetailViewModel
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.viewmodels.SettingsViewModel
import com.mazzzuta.travellog.viewmodels.TripsViewModel

val appModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "travellog.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }

    single { get<AppDatabase>().tripDao() }
    single { get<AppDatabase>().entryDao() }
    single { get<AppDatabase>().photoDao() }
    single { get<AppDatabase>().tagDao() }

    single { EntryRepository(get(), get(), get(), get()) }
    single { TripRepository(get()) }

    viewModel { FeedViewModel(get(), get(), get()) }
    viewModel { (entryId: Long) -> EntryDetailViewModel(entryId, get()) }

    single { GeocoderHelper(androidContext()) }
    single { LocationHelper(androidContext()) }
    single { PhotoStorageHelper(androidContext()) }

    single { UserPreferencesRepository(androidContext()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { TripsViewModel(get(), get()) }
    viewModel { MapViewModel(get(), get()) }

    viewModel { parameters -> CreateEntryViewModel(get(), get(), get(), get(), get(), parameters.getOrNull<Long>()) }
}
