package com.mazzzuta.travellog.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: Long,
    val endDate: Long? = null,
    val coverPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)