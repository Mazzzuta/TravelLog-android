package com.mazzzuta.travellog.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        EntryEntity::class,
        PhotoEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class
    ],
    version = 3,
    exportSchema = true
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun entryDao(): EntryDao
    abstract fun photoDao(): PhotoDao
    abstract fun tagDao(): TagDao

}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // DROP родительской таблицы каскадно удаляет фото и связи: сохраняем их до перестройки.
        db.execSQL("CREATE TEMP TABLE migration_photos AS SELECT * FROM photos")
        db.execSQL("CREATE TEMP TABLE migration_entry_tags AS SELECT * FROM entry_tag_cross_ref")
        db.execSQL("""
            CREATE TABLE entries_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tripId INTEGER, title TEXT NOT NULL, description TEXT NOT NULL,
                date INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL,
                placeName TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("INSERT INTO entries_new SELECT * FROM entries")
        db.execSQL("DROP TABLE entries")
        db.execSQL("ALTER TABLE entries_new RENAME TO entries")
        db.execSQL("CREATE INDEX index_entries_tripId ON entries(tripId)")
        db.execSQL("INSERT OR REPLACE INTO photos SELECT * FROM migration_photos")
        db.execSQL("INSERT OR REPLACE INTO entry_tag_cross_ref SELECT * FROM migration_entry_tags")
        db.execSQL("DROP TABLE migration_photos")
        db.execSQL("DROP TABLE migration_entry_tags")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TEMP TABLE migration_photos AS SELECT * FROM photos")
        db.execSQL("CREATE TEMP TABLE migration_entry_tags AS SELECT * FROM entry_tag_cross_ref")
        db.execSQL("""
            CREATE TABLE entries_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tripId INTEGER, title TEXT NOT NULL, description TEXT NOT NULL,
                date INTEGER NOT NULL, latitude REAL, longitude REAL,
                placeName TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        // Старое значение 0,0 без названия места означало отсутствие геолокации.
        db.execSQL("""
            INSERT INTO entries_new
            SELECT id, tripId, title, description, date,
                CASE WHEN latitude = 0 AND longitude = 0 AND (placeName IS NULL OR placeName = '') THEN NULL ELSE latitude END,
                CASE WHEN latitude = 0 AND longitude = 0 AND (placeName IS NULL OR placeName = '') THEN NULL ELSE longitude END,
                placeName, createdAt, updatedAt FROM entries
        """.trimIndent())
        db.execSQL("DROP TABLE entries")
        db.execSQL("ALTER TABLE entries_new RENAME TO entries")
        db.execSQL("CREATE INDEX index_entries_tripId ON entries(tripId)")
        db.execSQL("INSERT OR REPLACE INTO photos SELECT * FROM migration_photos")
        db.execSQL("INSERT OR REPLACE INTO entry_tag_cross_ref SELECT * FROM migration_entry_tags")
        db.execSQL("DROP TABLE migration_photos")
        db.execSQL("DROP TABLE migration_entry_tags")
    }
}
