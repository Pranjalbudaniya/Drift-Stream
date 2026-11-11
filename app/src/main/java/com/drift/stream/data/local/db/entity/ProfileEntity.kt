package com.drift.stream.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarColor: Int,
    val avatarEmoji: String?,
    val isOwner: Boolean,
    val needsSetup: Boolean,
    val pinHash: String?,
    val pinSalt: String?,
    val pinIterations: Int?,
    val createdAt: Long,
    val lastUsedAt: Long,
    val sortOrder: Int,
    val avatarCustomColor: Int? = null,
    val avatarPhotoStamp: Long? = null,
)
