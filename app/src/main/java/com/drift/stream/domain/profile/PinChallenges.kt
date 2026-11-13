package com.drift.stream.domain.profile

import kotlinx.coroutines.flow.StateFlow

sealed interface PinRequest {
    data class Verify(
        val profileId: Long,
        val purpose: PinPurpose,
    ) : PinRequest

    data object Create : PinRequest
}

enum class PinPurpose {
    EnterProfile,
    CurrentPinForChange,
    CurrentPinForRemove,
    OwnerReset,
    DeleteProfile,
    ManageProfiles,
}

sealed interface PinResult {
    data class Verified(
        val pin: String,
    ) : PinResult

    data class Created(
        val pin: String,
    ) : PinResult

    data object Cancelled : PinResult
}

data class PendingPin(
    val id: Long,
    val request: PinRequest,
)

interface PinChallenges {
    val pending: StateFlow<PendingPin?>

    suspend fun ask(request: PinRequest): PinResult

    fun finish(
        id: Long,
        result: PinResult,
    )
}
