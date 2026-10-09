package com.mazzzuta.travellog.utils

import androidx.compose.runtime.compositionLocalOf
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Формат отображения даты, выбираемый в настройках. */
enum class DateFormatOption(val id: String, val label: String, val pattern: String) {
    DMY("dmy", "дд.мм.гггг", "dd.MM.yyyy"),
    MDY("mdy", "мм/дд/гггг", "MM/dd/yyyy");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: DMY
    }
}

/** Текущий формат даты доступен любому экрану без передачи через параметры. */
val LocalDateFormat = compositionLocalOf { DateFormatOption.DMY }

// DatePicker представляет календарный день полуночью UTC, а записи отображаются в локальном поясе.
fun toDatePickerMillis(timestampMillis: Long): Long = Instant.ofEpochMilli(timestampMillis)
    .atZone(ZoneId.systemDefault()).toLocalDate()
    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun fromDatePickerMillis(timestampMillis: Long): Long = Instant.ofEpochMilli(timestampMillis)
    .atZone(ZoneOffset.UTC).toLocalDate()
    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun endOfDayMillis(timestampMillis: Long): Long = Instant.ofEpochMilli(timestampMillis)
    .atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

/** Форматирует метку времени (мс) по выбранному формату. */
fun formatDate(timestampMillis: Long, format: DateFormatOption): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern(format.pattern))

/** «апреля 2025» (родительный падеж) для фразы «Путешественник с апреля 2025». */
fun formatMonthYear(timestampMillis: Long): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ru")))
