package com.mazzzuta.travellog.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    // Один запрос закрывает сразу 3 требования: поиск, фильтр по поездке, фильтр по датам, сортировку
    @Transaction
    @Query("""
        SELECT * FROM entries 
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        AND (:tripId IS NULL OR tripId = :tripId)
        AND (:dateFrom IS NULL OR date >= :dateFrom)
        AND (:dateTo IS NULL OR date <= :dateTo)
        ORDER BY 
            CASE WHEN :sortBy = 'date_desc' THEN date END DESC,
            CASE WHEN :sortBy = 'date_asc' THEN date END ASC,
            CASE WHEN :sortBy = 'title' THEN title END ASC
    """)
    fun searchEntries(
        query: String = "",
        tripId: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        sortBy: String = "date_desc"
    ): Flow<List<EntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :entryId")
    fun getEntryWithDetails(entryId: Long): Flow<EntryWithDetails>

    // данные для экрана статистики — столбчатый график по месяцам
    @Query("SELECT strftime('%Y-%m', date/1000, 'unixepoch') as month, COUNT(*) as count FROM entries GROUP BY month ORDER BY month")
    fun getEntryCountByMonth(): Flow<List<MonthCount>>

    @Query("SELECT COUNT(*) FROM entries")
    fun getTotalEntriesCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT tripId) FROM entries")
    fun getTotalTripsWithEntriesCount(): Flow<Int>
}