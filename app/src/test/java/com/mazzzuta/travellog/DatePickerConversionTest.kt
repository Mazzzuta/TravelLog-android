package com.mazzzuta.travellog

import com.mazzzuta.travellog.utils.fromDatePickerMillis
import com.mazzzuta.travellog.utils.toDatePickerMillis
import com.mazzzuta.travellog.utils.isFutureDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class DatePickerConversionTest {
    @Test fun futureDatesUseLocalDayAndAllowAllOfToday() {
        val today = LocalDate.of(2026, 10, 12)
        for (name in listOf("Asia/Chita", "America/Los_Angeles")) {
            val zone = ZoneId.of(name)
            fun timestamp(day: LocalDate, hour: Int) = day.atTime(hour, 59).atZone(zone).toInstant().toEpochMilli()
            assertFalse(isFutureDate(timestamp(today.minusDays(1), 23), zone, today))
            assertFalse(isFutureDate(timestamp(today, 23), zone, today))
            assertTrue(isFutureDate(timestamp(today.plusDays(1), 0), zone, today))
        }
    }

    @Test
    fun pickerPreservesCalendarDayAcrossTimeZones() {
        val originalZone = TimeZone.getDefault()
        try {
            for (zone in listOf("Asia/Chita", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (day in listOf("2024-02-29", "2026-03-08", "2026-11-01")) {
                    val date = LocalDate.parse(day)
                    val localTimestamp = date.atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val pickerTimestamp = Instant.parse("${day}T00:00:00Z").toEpochMilli()
                    assertEquals(pickerTimestamp, toDatePickerMillis(localTimestamp))
                    assertEquals(date, Instant.ofEpochMilli(fromDatePickerMillis(pickerTimestamp))
                        .atZone(ZoneId.systemDefault()).toLocalDate())
                }
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
}
