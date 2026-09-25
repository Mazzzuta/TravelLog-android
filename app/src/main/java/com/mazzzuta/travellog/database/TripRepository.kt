package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TripRepository(private val tripDao: TripDao) {
    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()
    fun getTripWithEntries(tripId: Long): Flow<TripWithEntries> = tripDao.getTripWithEntries(tripId)
    suspend fun createTrip(trip: TripEntity): Long = tripDao.insert(trip)
    suspend fun updateTrip(trip: TripEntity) = tripDao.update(trip)
    suspend fun deleteTrip(trip: TripEntity) = tripDao.delete(trip)

    suspend fun ensureDefaultTrip(): Long {
        val trips = tripDao.getAllTrips().first()
        return trips.firstOrNull()?.id
            ?: tripDao.insert(TripEntity(title = "Мои путешествия", startDate = System.currentTimeMillis()))
    }
}