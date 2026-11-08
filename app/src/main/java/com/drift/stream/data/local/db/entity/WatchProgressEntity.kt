package com.drift.stream.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "key"],
)
data class WatchProgressEntity(
    val key: String,
    val metaId: String,
    val videoId: String,
    val type: String,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1L,
)
