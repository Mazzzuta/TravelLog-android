package com.mazzzuta.travellog.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        EntryEntity::class,
        PhotoEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class
    ],
    version = 1,
    exportSchema = true
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun entryDao(): EntryDao
    abstract fun photoDao(): PhotoDao
    abstract fun tagDao(): TagDao

}