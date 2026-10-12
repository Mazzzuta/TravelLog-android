package com.mazzzuta.travellog.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "trips")
@Serializable
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: Long,
    val endDate: Long? = null,
    val coverPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
