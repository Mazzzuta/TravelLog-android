package com.mazzzuta.travellog.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class EntryWithDetails(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val photos: List<PhotoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

data class TripWithEntries(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val entries: List<EntryEntity>
)