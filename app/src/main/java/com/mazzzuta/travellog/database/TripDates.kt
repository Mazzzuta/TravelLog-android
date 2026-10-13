package com.mazzzuta.travellog.database

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun Long.day(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun TripEntity.containsDate(date: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    date.day(zone) >= startDate.day(zone) && (endDate == null || date.day(zone) <= endDate.day(zone))

internal fun TripEntity.overlaps(other: TripEntity, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    startDate.day(zone) <= (other.endDate?.day(zone) ?: LocalDate.MAX) &&
        other.startDate.day(zone) <= (endDate?.day(zone) ?: LocalDate.MAX)

internal fun validateTripChange(trip: TripEntity, others: List<TripEntity>, entries: List<EntryEntity> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault()) {
    validateTrip(trip)
    val conflict = others.firstOrNull { it.id != trip.id && trip.overlaps(it, zone) }
    require(conflict == null) { "Период пересекается с поездкой «${conflict?.title}». Выберите другие даты" }
    require(entries.all { trip.containsDate(it.date, zone) }) {
        "В поездке есть записи вне нового периода. Сначала измените их даты или выберите для них «Без поездки»"
    }
}

internal fun resolveEntryTrip(entry: EntryEntity, trips: List<TripEntity>, autoAssign: Boolean,
    zone: ZoneId = ZoneId.systemDefault()): EntryEntity {
    if (autoAssign) {
        val matches = trips.filter { it.containsDate(entry.date, zone) }
        require(matches.size <= 1) { "На дату записи найдено несколько поездок. Сначала исправьте пересечение их периодов" }
        return entry.copy(tripId = matches.singleOrNull()?.id)
    }
    val id = entry.tripId ?: return entry
    val trip = trips.firstOrNull { it.id == id }
    require(trip != null) { "Поездка больше не существует" }
    require(trip.containsDate(entry.date, zone)) { "Дата записи не входит в период поездки «${trip.title}»" }
    return entry
}

internal fun validateBackupTripDates(trips: List<TripEntity>, entries: List<EntryEntity>) {
    val sorted = trips.sortedBy { it.startDate }
    // После сортировки достаточно проверить соседние периоды: любое пересечение проявится между ними.
    sorted.zipWithNext().forEach { (first, second) ->
        require(!first.overlaps(second)) { "В архиве пересекаются поездки «${first.title}» и «${second.title}»" }
    }
    val byId = trips.associateBy { it.id }
    require(entries.all { entry -> entry.tripId?.let { byId[it]?.containsDate(entry.date) == true } ?: true }) {
        "В архиве есть записи вне периода своей поездки. Исправьте даты или выберите «Без поездки» перед экспортом"
    }
}
