package com.drift.stream.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.drift.stream.domain.model.AddonSource

@Entity(
    tableName = "addons",
    primaryKeys = ["profileId", "transportUrl"],
    indices = [Index(value = ["position"])],
)
data class AddonEntity(
    val transportUrl: String,
    val id: String,
    val name: String,
    val version: String,
    val manifestJson: String,
    val enabled: Boolean,
    val position: Int,
    val source: AddonSource,
    val installedAt: Long,
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1L,
)
