package com.mazzzuta.travellog.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val title: String,
    val description: String,
    val date: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun EntryEntity.hasCoordinates(): Boolean =
    latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0
