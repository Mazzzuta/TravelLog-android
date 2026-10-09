package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

class EntryRepository(
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
    private val tagDao: TagDao,
    private val database: AppDatabase,
) {
    fun searchEntries(
        query: String = "",
        tripId: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        sortBy: String = "date_desc"
    ): Flow<List<EntryWithDetails>> =
        entryDao.searchEntries(query, tripId, dateFrom, dateTo, sortBy)

    fun getEntryWithDetails(entryId: Long): Flow<EntryWithDetails?> =
        entryDao.getEntryWithDetails(entryId)

    suspend fun createEntry(entry: EntryEntity, photoPaths: List<String>, tagIds: List<Long>): Long = database.withTransaction {
        val entryId = entryDao.insert(entry)
        if (photoPaths.isNotEmpty()) {
            photoDao.insertAll(photoPaths.mapIndexed { index, path ->
                PhotoEntity(entryId = entryId, filePath = path, orderIndex = index)
            })
        }
        tagIds.forEach { tagId ->
            tagDao.addTagToEntry(EntryTagCrossRef(entryId = entryId, tagId = tagId))
        }
        entryId
    }

    suspend fun updateEntry(entry: EntryEntity, photoPaths: List<String>, tagIds: List<Long>) {
        database.withTransaction {
            check(entryDao.getEntryWithDetails(entry.id).first() != null) { "Запись уже удалена" }
            entryDao.update(entry)
            photoDao.deleteForEntry(entry.id)
            photoDao.insertAll(photoPaths.mapIndexed { index, path ->
                PhotoEntity(entryId = entry.id, filePath = path, orderIndex = index)
            })
            tagDao.removeTagsForEntry(entry.id)
            tagIds.forEach { tagId ->
                tagDao.addTagToEntry(EntryTagCrossRef(entry.id, tagId))
            }
        }
    }
    suspend fun deleteEntry(entry: EntryEntity) = entryDao.delete(entry)

    fun getEntryCountByMonth(): Flow<List<MonthCount>> = entryDao.getEntryCountByMonth()
    fun getTotalEntriesCount(): Flow<Int> = entryDao.getTotalEntriesCount()
    fun getTagUsageStats(): Flow<List<TagCount>> = tagDao.getTagUsageStats()
    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()
    suspend fun createTag(name: String): Long = tagDao.insert(TagEntity(name = name))
}
