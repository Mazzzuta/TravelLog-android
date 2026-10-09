package com.mazzzuta.travellog

import com.mazzzuta.travellog.utils.fromDatePickerMillis
import com.mazzzuta.travellog.utils.toDatePickerMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class DatePickerConversionTest {
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
