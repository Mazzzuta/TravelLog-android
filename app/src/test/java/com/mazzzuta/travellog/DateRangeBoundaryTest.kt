package com.mazzzuta.travellog

import com.mazzzuta.travellog.utils.endOfDayMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class DateRangeBoundaryTest {
    @Test
    fun endOfDayIncludesEntireDayAcrossDaylightSavingChanges() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            for ((day, hours) in listOf("2026-03-08" to 23, "2026-11-01" to 25)) {
                val start = LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                assertEquals(hours * 60 * 60 * 1000L - 1, endOfDayMillis(start) - start)
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
