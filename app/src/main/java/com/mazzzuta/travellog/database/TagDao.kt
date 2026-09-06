package com.mazzzuta.travellog.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToEntry(crossRef: EntryTagCrossRef)

    @Delete
    suspend fun removeTagFromEntry(crossRef: EntryTagCrossRef)

    // данные для круговой диаграммы на экране статистики
    @Query("""
        SELECT tags.name as tagName, COUNT(entry_tag_cross_ref.entryId) as count 
        FROM tags 
        LEFT JOIN entry_tag_cross_ref ON tags.id = entry_tag_cross_ref.tagId 
        GROUP BY tags.id
    """)
    fun getTagUsageStats(): Flow<List<TagCount>>
}