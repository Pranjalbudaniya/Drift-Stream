package com.drift.stream.domain.model

data class Profile(
    val id: Long,
    val name: String,
    val avatarColor: Int,
    val avatarEmoji: String?,
    val isOwner: Boolean,
    val needsSetup: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long,
    val sortOrder: Int,
    val avatarCustomColor: Int? = null,
    val avatarPhotoStamp: Long? = null,
    val isLocked: Boolean = false,
)
