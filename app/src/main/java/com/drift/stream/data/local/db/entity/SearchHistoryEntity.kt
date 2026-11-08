package com.drift.stream.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "search_history",
    primaryKeys = ["profileId", "query"],
)
data class SearchHistoryEntity(
    val query: String,
    val lastUsedAt: Long,
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1L,
)
