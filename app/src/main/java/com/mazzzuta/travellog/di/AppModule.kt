package com.mazzzuta.travellog.di

import androidx.room.Room
import com.mazzzuta.travellog.database.AppDatabase
import com.mazzzuta.travellog.database.EntryRepository
import com.mazzzuta.travellog.database.TripRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "travellog.db"
        ).build()
    }

    single { get<AppDatabase>().tripDao() }
    single { get<AppDatabase>().entryDao() }
    single { get<AppDatabase>().photoDao() }
    single { get<AppDatabase>().tagDao() }

    single { EntryRepository(get(), get(), get()) }
    single { TripRepository(get()) }
}