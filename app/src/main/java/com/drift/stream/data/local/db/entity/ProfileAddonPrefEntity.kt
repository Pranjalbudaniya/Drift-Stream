package com.drift.stream.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "profile_addon_prefs",
    primaryKeys = ["profileId", "ownerProfileId", "transportUrl"],
)
data class ProfileAddonPrefEntity(
    val profileId: Long,
    val ownerProfileId: Long,
    val transportUrl: String,
    val enabled: Boolean,
)
