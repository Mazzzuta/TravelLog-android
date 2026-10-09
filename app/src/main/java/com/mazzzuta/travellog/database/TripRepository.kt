package com.mazzzuta.travellog.database

import kotlinx.coroutines.flow.Flow

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
    // shortcut: каскад удаляет строки, файлы фото остаются; очистку добавить с учётом обложек других поездок.
    suspend fun deleteTrip(trip: TripEntity, deleteEntries: Boolean = false) {
        if (deleteEntries) tripDao.delete(trip) else tripDao.deleteKeepingEntries(trip)
    }

}

internal fun validateTrip(trip: TripEntity) {
    require(trip.title.isNotBlank()) { "Введите название поездки" }
    require(trip.endDate == null || trip.endDate >= trip.startDate) { "Дата окончания раньше начала поездки" }
}
