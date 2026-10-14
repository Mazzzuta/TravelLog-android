package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.mazzzuta.travellog.utils.isFutureDate

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
        sortBy: String = "date_desc",
        onlyWithoutTrip: Boolean = false,
    ): Flow<List<EntryWithDetails>> =
        entryDao.searchEntries(query, tripId, dateFrom, dateTo, sortBy, onlyWithoutTrip)

    fun getEntryWithDetails(entryId: Long): Flow<EntryWithDetails?> =
        entryDao.getEntryWithDetails(entryId)

    fun getMapEntries(): Flow<List<EntryWithDetails>> = entryDao.getMapEntries()

    // Для статистики не нужны описания и списки фото; все счётчики читаем из одного состояния БД.
    fun observeStatistics(): Flow<StatisticsData> = database.invalidationTracker
        .createFlow("entries", "trips", "photos", "tags", "entry_tag_cross_ref")
        .map {
            database.withTransaction {
                StatisticsData(entryDao.getStatisticsDates(), entryDao.getStatisticsTotals(), tagDao.getTagUsageStats().first())
            }
        }.distinctUntilChanged()

    // Проверяем поездку и сохраняем запись с фото и тегами в одной транзакции.
    suspend fun createEntry(entry: EntryEntity, photoPaths: List<String>, tagIds: List<Long>, autoAssignTrip: Boolean = false): Long = database.withTransaction {
        require(!isFutureDate(entry.date)) { "Дата записи не может быть позже сегодняшнего дня" }
        val resolved = resolveEntryTrip(entry, database.tripDao().getAllTrips().first(), autoAssignTrip)
        val entryId = entryDao.insert(resolved)
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

    suspend fun updateEntry(entry: EntryEntity, photoPaths: List<String>, tagIds: List<Long>, autoAssignTrip: Boolean = false) {
        database.withTransaction {
            require(!isFutureDate(entry.date)) { "Дата записи не может быть позже сегодняшнего дня" }
            check(entryDao.getEntryWithDetails(entry.id).first() != null) { "Запись уже удалена" }
            val resolved = resolveEntryTrip(entry, database.tripDao().getAllTrips().first(), autoAssignTrip)
            entryDao.update(resolved)
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
    suspend fun deleteTags(ids: List<Long>) = database.withTransaction {
        ids.chunked(900).forEach { tagDao.deleteTags(it) }
    }

    suspend fun createTag(name: String): Long = database.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Введите название тега" }
        require(trimmed.length <= MAX_TAG_LENGTH) { "Название тега — не больше $MAX_TAG_LENGTH символов" }
        // «Лес» и « лес » считаем одним тегом.
        tagDao.getAllTags().first().firstOrNull { it.name.trim().equals(trimmed, ignoreCase = true) }?.id
            ?: tagDao.insert(TagEntity(name = trimmed))
    }
}
