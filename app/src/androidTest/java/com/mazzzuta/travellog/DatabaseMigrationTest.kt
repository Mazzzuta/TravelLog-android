package com.mazzzuta.travellog

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseMigrationTest {
    @Test
    fun versionOneMigratesWithoutLosingEntryPhotosOrTags() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.setForeignKeyConstraintsEnabled(true)
            old.execSQL("""CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `coverPhotoPath` TEXT, `createdAt` INTEGER NOT NULL)""")
            old.execSQL("""CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `placeName` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
            old.execSQL("""CREATE INDEX IF NOT EXISTS `index_entries_tripId` ON `entries` (`tripId`)""")
            old.execSQL("""CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
            old.execSQL("""CREATE INDEX IF NOT EXISTS `index_photos_entryId` ON `photos` (`entryId`)""")
            old.execSQL("""CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT)""")
            old.execSQL("""CREATE TABLE IF NOT EXISTS `entry_tag_cross_ref` (`entryId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `tagId`), FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
            old.execSQL("""CREATE INDEX IF NOT EXISTS `index_entry_tag_cross_ref_entryId` ON `entry_tag_cross_ref` (`entryId`)""")
            old.execSQL("""CREATE INDEX IF NOT EXISTS `index_entry_tag_cross_ref_tagId` ON `entry_tag_cross_ref` (`tagId`)""")
            old.execSQL("""CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)""")
            old.execSQL("""INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '724cd0f1a0470bbdc36313a2d2daceef')""")
                old.execSQL("INSERT INTO trips VALUES (1, 'Trip', 1000, NULL, NULL, 1000)")
                old.execSQL("INSERT INTO entries VALUES (1, 1, 'Title', 'Text', 2000, 1.0, 2.0, 'Place', 3000, 4000)")
                old.execSQL("INSERT INTO entries VALUES (2, 1, 'No location', '', 2000, 0.0, 0.0, NULL, 3000, 4000)")
                old.execSQL("INSERT INTO photos VALUES (1, 1, 'photo.jpg', 0)")
                old.execSQL("INSERT INTO tags VALUES (1, 'Tag', NULL)")
                old.execSQL("INSERT INTO entry_tag_cross_ref VALUES (1, 1)")
                old.version = 1
            }
            val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            try {
                val repository = EntryRepository(database.entryDao(), database.photoDao(), database.tagDao(), database)
                val migrated = repository.getEntryWithDetails(1L).first()!!
                assertEquals(1L, migrated.entry.tripId)
                assertEquals("Title", migrated.entry.title)
                assertEquals(2000L, migrated.entry.date)
                assertEquals(3000L, migrated.entry.createdAt)
                assertEquals(4000L, migrated.entry.updatedAt)
                assertEquals(listOf("photo.jpg"), migrated.photos.map { it.filePath })
                assertEquals(listOf("Tag"), migrated.tags.map { it.name })
                assertEquals(1.0, migrated.entry.latitude!!, 0.0)
                assertNull(repository.getEntryWithDetails(2L).first()!!.entry.latitude)
                assertNull(repository.getEntryWithDetails(2L).first()!!.entry.longitude)
                repository.updateEntry(migrated.entry.copy(tripId = null), listOf("photo.jpg"), listOf(1L))
                database.tripDao().delete(database.tripDao().getAllTrips().first().single())
                assertNull(repository.getEntryWithDetails(1L).first()!!.entry.tripId)
                assertEquals(listOf("photo.jpg"), repository.getEntryWithDetails(1L).first()!!.photos.map { it.filePath })
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
