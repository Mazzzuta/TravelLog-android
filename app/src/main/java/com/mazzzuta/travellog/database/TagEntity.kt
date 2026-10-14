package com.mazzzuta.travellog.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

const val MAX_TAG_LENGTH = 30

@Entity(tableName = "tags")
@Serializable
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String? = null
)
