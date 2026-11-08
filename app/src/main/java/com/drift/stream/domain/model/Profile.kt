package com.drift.stream.domain.model

data class Profile(
    val id: Long,
    val name: String,
    val avatarColor: Int,
    val avatarEmoji: String?,
    val isOwner: Boolean,
    val needsSetup: Boolean,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinIterations: Int? = null,
    val createdAt: Long,
    val lastUsedAt: Long,
    val sortOrder: Int,
)
