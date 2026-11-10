package com.drift.stream.data.local.db.dao

import androidx.room.Embedded
import com.drift.stream.data.local.db.entity.AddonEntity

data class SharedAddonRow(
    @Embedded val addon: AddonEntity,
    val ownerName: String,
    val enabledForMe: Boolean,
)
