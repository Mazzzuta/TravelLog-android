package com.mazzzuta.travellog.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable data object Feed : Route
    @Serializable data object MapScreen : Route
    @Serializable data object CreateEntry : Route
    @Serializable data class EntryDetail(val entryId: Long) : Route
    @Serializable data object Statistics : Route
    @Serializable data object Settings : Route
    @Serializable data object Trips : Route
}

fun Route.hasBottomNav(): Boolean = when (this) {
    is Route.CreateEntry, is Route.EntryDetail, is Route.Trips -> false
    else -> true
}