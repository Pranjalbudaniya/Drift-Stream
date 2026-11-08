package com.drift.stream.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "library_items",
    primaryKeys = ["profileId", "metaId"],
)
data class LibraryItemEntity(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val inWatchlist: Boolean,
    val addedAt: Long,
    @ColumnInfo(name = "listStatus", defaultValue = "PLAN_TO_WATCH")
    val listStatus: String = "PLAN_TO_WATCH",
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1L,
)
