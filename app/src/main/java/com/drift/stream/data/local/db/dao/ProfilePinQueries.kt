package com.drift.stream.data.local.db.dao

import androidx.room.Query
import com.drift.stream.data.local.db.entity.ProfilePinEntity

interface ProfilePinQueries {
    @Query(
        "SELECT pinHash, pinSalt, pinIterations, pinFailedAttempts, pinLockedUntil " +
            "FROM profiles WHERE id = :id LIMIT 1",
    )
    suspend fun getPinRow(id: Long): ProfilePinEntity?

    @Query(
        "UPDATE profiles SET pinHash = :hash, pinSalt = :salt, pinIterations = :iterations, " +
            "pinFailedAttempts = NULL, pinLockedUntil = NULL WHERE id = :id",
    )
    suspend fun setPin(
        id: Long,
        hash: String,
        salt: String,
        iterations: Int,
    )

    @Query(
        "UPDATE profiles SET pinHash = NULL, pinSalt = NULL, pinIterations = NULL, " +
            "pinFailedAttempts = NULL, pinLockedUntil = NULL WHERE id = :id",
    )
    suspend fun clearPin(id: Long)

    @Query("UPDATE profiles SET pinFailedAttempts = :attempts, pinLockedUntil = :lockedUntil WHERE id = :id")
    suspend fun recordPinFailure(
        id: Long,
        attempts: Int,
        lockedUntil: Long?,
    )

    @Query("UPDATE profiles SET pinFailedAttempts = NULL, pinLockedUntil = NULL WHERE id = :id")
    suspend fun resetPinFailures(id: Long)
}
