package com.mazzzuta.travellog.navigation

enum class BottomTab(val route: Route, val label: String) {
    FEED(Route.Feed, "Лента"),
    MAP(Route.MapScreen, "Карта"),
    STATISTICS(Route.Statistics, "Статистика"),
    SETTINGS(Route.Settings, "Настройки"),
}