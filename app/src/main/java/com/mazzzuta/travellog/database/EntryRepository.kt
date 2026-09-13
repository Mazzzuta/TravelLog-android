package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow

class EntryRepository(
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
    private val tagDao: TagDao
) {
    fun searchEntries(
        query: String = "",
        tripId: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        sortBy: String = "date_desc"
    ): Flow<List<EntryWithDetails>> =
        entryDao.searchEntries(query, tripId, dateFrom, dateTo, sortBy)

    fun getEntryWithDetails(entryId: Long): Flow<EntryWithDetails> =
        entryDao.getEntryWithDetails(entryId)

    suspend fun createEntry(entry: EntryEntity, photoPaths: List<String>, tagIds: List<Long>): Long {
        val entryId = entryDao.insert(entry)
        if (photoPaths.isNotEmpty()) {
            photoDao.insertAll(photoPaths.mapIndexed { index, path ->
                PhotoEntity(entryId = entryId, filePath = path, orderIndex = index)
            })
        }
        tagIds.forEach { tagId ->
            tagDao.addTagToEntry(EntryTagCrossRef(entryId = entryId, tagId = tagId))
        }
        return entryId
    }

    suspend fun updateEntry(entry: EntryEntity) = entryDao.update(entry)
    suspend fun deleteEntry(entry: EntryEntity) = entryDao.delete(entry)

    fun getEntryCountByMonth(): Flow<List<MonthCount>> = entryDao.getEntryCountByMonth()
    fun getTotalEntriesCount(): Flow<Int> = entryDao.getTotalEntriesCount()
    fun getTagUsageStats(): Flow<List<TagCount>> = tagDao.getTagUsageStats()
    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()
}