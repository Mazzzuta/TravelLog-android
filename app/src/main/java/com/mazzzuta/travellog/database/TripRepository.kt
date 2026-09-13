package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {
    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()
    fun getTripWithEntries(tripId: Long): Flow<TripWithEntries> = tripDao.getTripWithEntries(tripId)
    suspend fun createTrip(trip: TripEntity): Long = tripDao.insert(trip)
    suspend fun updateTrip(trip: TripEntity) = tripDao.update(trip)
    suspend fun deleteTrip(trip: TripEntity) = tripDao.delete(trip)
}