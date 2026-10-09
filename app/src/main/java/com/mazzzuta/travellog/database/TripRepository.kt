package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TripRepository(private val tripDao: TripDao) {
    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()
    fun getTripWithEntries(tripId: Long): Flow<TripWithEntries> = tripDao.getTripWithEntries(tripId)
    suspend fun createTrip(trip: TripEntity): Long {
        validateTrip(trip)
        return tripDao.insert(trip.copy(title = trip.title.trim()))
    }
    suspend fun updateTrip(trip: TripEntity) {
        validateTrip(trip)
        tripDao.update(trip.copy(title = trip.title.trim()))
    }
    suspend fun deleteTrip(trip: TripEntity) = tripDao.delete(trip)

    suspend fun ensureDefaultTrip(): Long {
        val trips = tripDao.getAllTrips().first()
        return trips.firstOrNull()?.id
            ?: tripDao.insert(TripEntity(title = "Мои путешествия", startDate = System.currentTimeMillis()))
    }
}

internal fun validateTrip(trip: TripEntity) {
    require(trip.title.isNotBlank()) { "Введите название поездки" }
    require(trip.endDate == null || trip.endDate >= trip.startDate) { "Дата окончания раньше начала поездки" }
}
