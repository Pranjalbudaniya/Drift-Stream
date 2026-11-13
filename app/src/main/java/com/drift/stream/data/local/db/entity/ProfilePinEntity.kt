package com.drift.stream.data.local.db.entity

data class ProfilePinEntity(
    val pinHash: String?,
    val pinSalt: String?,
    val pinIterations: Int?,
    val pinFailedAttempts: Int?,
    val pinLockedUntil: Long?,
)
