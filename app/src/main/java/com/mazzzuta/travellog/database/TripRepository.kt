package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction

class TripRepository(private val tripDao: TripDao, private val database: AppDatabase) {
    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()
    fun getTripWithEntries(tripId: Long): Flow<TripWithEntries> = tripDao.getTripWithEntries(tripId)
    // Без общей транзакции две одновременные проверки могли бы пропустить пересекающиеся поездки.
    suspend fun createTrip(trip: TripEntity): Long = database.withTransaction {
        validateTripChange(trip, tripDao.getAllTrips().first())
        tripDao.insert(trip.copy(title = trip.title.trim()))
    }
    suspend fun updateTrip(trip: TripEntity) {
        database.withTransaction {
            val trips = tripDao.getAllTrips().first()
            require(trips.any { it.id == trip.id }) { "Поездка уже удалена" }
            validateTripChange(trip, trips, tripDao.getTripWithEntries(trip.id).first().entries)
            tripDao.update(trip.copy(title = trip.title.trim()))
        }
    }
    suspend fun deleteTrip(trip: TripEntity, deleteEntries: Boolean = false) {
        if (deleteEntries) tripDao.delete(trip) else tripDao.deleteKeepingEntries(trip)
    }

}

internal fun validateTrip(trip: TripEntity) {
    require(trip.title.isNotBlank()) { "Введите название поездки" }
    require(trip.endDate == null || trip.endDate >= trip.startDate) { "Дата окончания раньше начала поездки" }
}
