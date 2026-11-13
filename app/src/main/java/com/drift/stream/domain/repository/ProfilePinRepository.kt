package com.drift.stream.domain.repository

sealed interface SetPinResult {
    data object Set : SetPinResult

    data object InvalidPin : SetPinResult

    data object NotFound : SetPinResult
}

sealed interface VerifyResult {
    data object Correct : VerifyResult

    data class Wrong(
        val triesLeft: Int,
    ) : VerifyResult

    data class Locked(
        val remainingMs: Long,
    ) : VerifyResult

    data object NoPin : VerifyResult

    data object NotFound : VerifyResult
}

interface ProfilePinRepository {
    suspend fun setPin(
        profileId: Long,
        pin: String,
    ): SetPinResult

    suspend fun removePin(profileId: Long)

    suspend fun verify(
        profileId: Long,
        pin: String,
    ): VerifyResult

    suspend fun remainingLock(profileId: Long): Long
}
