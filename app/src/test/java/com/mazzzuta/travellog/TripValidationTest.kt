package com.mazzzuta.travellog

import com.mazzzuta.travellog.database.TripEntity
import com.mazzzuta.travellog.database.validateTrip
import org.junit.Test

class TripValidationTest {
    @Test(expected = IllegalArgumentException::class)
    fun blankTitleIsRejected() {
        validateTrip(TripEntity(title = "  ", startDate = 1000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun endBeforeStartIsRejected() {
        validateTrip(TripEntity(title = "Поездка", startDate = 2000L, endDate = 1000L))
    }

    @Test
    fun ongoingAndSingleDayTripsAreAllowed() {
        validateTrip(TripEntity(title = "Поездка", startDate = 1000L))
        validateTrip(TripEntity(title = "Поездка", startDate = 1000L, endDate = 1000L))
    }
}
