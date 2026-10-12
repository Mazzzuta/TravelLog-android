package com.mazzzuta.travellog.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BackupDao {
    @Query("SELECT * FROM trips ORDER BY id") suspend fun trips(): List<TripEntity>
    @Query("SELECT * FROM entries ORDER BY id") suspend fun entries(): List<EntryEntity>
    @Query("SELECT * FROM photos ORDER BY id") suspend fun photos(): List<PhotoEntity>
    @Query("SELECT * FROM tags ORDER BY id") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM entry_tag_cross_ref ORDER BY entryId, tagId") suspend fun links(): List<EntryTagCrossRef>
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM trips") suspend fun clearTrips()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Insert suspend fun insertTrips(values: List<TripEntity>)
    @Insert suspend fun insertEntries(values: List<EntryEntity>)
    @Insert suspend fun insertPhotos(values: List<PhotoEntity>)
    @Insert suspend fun insertTags(values: List<TagEntity>)
    @Insert suspend fun insertLinks(values: List<EntryTagCrossRef>)
}
