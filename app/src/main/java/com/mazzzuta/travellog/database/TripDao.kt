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
interface TripDao {

    // insert возвращает id новой строки — пригодится, чтобы сразу открыть созданную поездку
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Delete
    suspend fun delete(trip: TripEntity)

    // Flow — экран сам обновится, когда список поездок изменится в БД
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    // @Transaction нужен, когда запрос требует JOIN через Relation (см. Relations.kt дальше)
    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun getTripWithEntries(tripId: Long): Flow<TripWithEntries>
}