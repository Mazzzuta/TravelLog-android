package com.mazzzuta.travellog.database

data class StatisticsTotals(val trips: Int, val photos: Int, val places: Int)

data class StatisticsData(val dates: List<Long>, val totals: StatisticsTotals, val tags: List<TagCount>)
