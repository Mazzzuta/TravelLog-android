package com.mazzzuta.travellog

import com.mazzzuta.travellog.navigation.Route
import com.mazzzuta.travellog.navigation.hasBottomNav
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class EntryMapRouteTest {
    @Test fun focusedMapRetainsEntryIdAndUsesSeparateBackNavigation() {
        val route: Route = Route.EntryMap(42L)
        assertEquals(route, Json.decodeFromString<Route>(Json.encodeToString(route)))
        assertFalse(route.hasBottomNav())
        assertTrue(Route.MapScreen.hasBottomNav())
    }
}
