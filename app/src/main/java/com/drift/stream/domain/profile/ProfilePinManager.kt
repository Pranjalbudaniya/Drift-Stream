package com.drift.stream.domain.profile

import com.drift.stream.domain.repository.ProfilePinRepository
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.SetPinResult
import com.drift.stream.domain.repository.VerifyResult
import javax.inject.Inject

sealed interface PinManagerSetResult {
    data object Set : PinManagerSetResult

    data object AlreadyLocked : PinManagerSetResult

    data object InvalidPin : PinManagerSetResult

    data object NotFound : PinManagerSetResult
}

sealed interface PinManagerChangeResult {
    data object Changed : PinManagerChangeResult

    data class Wrong(
        val triesLeft: Int,
    ) : PinManagerChangeResult

    data class Locked(
        val remainingMs: Long,
    ) : PinManagerChangeResult

    data object InvalidPin : PinManagerChangeResult

    data object NoPin : PinManagerChangeResult

    data object NotFound : PinManagerChangeResult
}

sealed interface PinManagerRemoveResult {
    data object Removed : PinManagerRemoveResult

    data class Wrong(
        val triesLeft: Int,
    ) : PinManagerRemoveResult

    data class Locked(
        val remainingMs: Long,
    ) : PinManagerRemoveResult

    data object NoPin : PinManagerRemoveResult

    data object NotFound : PinManagerRemoveResult
}

class ProfilePinManager
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val profilePinRepository: ProfilePinRepository,
    ) {
        suspend fun setPin(
            profileId: Long,
            pin: String,
        ): PinManagerSetResult {
            if (!isValidPin(pin)) return PinManagerSetResult.InvalidPin
            val profile = profileRepository.get(profileId)
            return when {
                profile == null -> {
                    PinManagerSetResult.NotFound
                }

                profile.isLocked -> {
                    PinManagerSetResult.AlreadyLocked
                }

                else -> {
                    when (profilePinRepository.setPin(profileId, pin)) {
                        is SetPinResult.Set -> PinManagerSetResult.Set
                        is SetPinResult.InvalidPin -> PinManagerSetResult.InvalidPin
                        is SetPinResult.NotFound -> PinManagerSetResult.NotFound
                    }
                }
            }
        }

        suspend fun changePin(
            profileId: Long,
            currentPin: String,
            newPin: String,
        ): PinManagerChangeResult {
            if (!isValidPin(newPin)) return PinManagerChangeResult.InvalidPin
            val verifyResult = profilePinRepository.verify(profileId, currentPin)
            return when (verifyResult) {
                is VerifyResult.Correct -> {
                    when (profilePinRepository.setPin(profileId, newPin)) {
                        is SetPinResult.Set -> PinManagerChangeResult.Changed
                        is SetPinResult.InvalidPin -> PinManagerChangeResult.InvalidPin
                        is SetPinResult.NotFound -> PinManagerChangeResult.NotFound
                    }
                }

                is VerifyResult.Wrong -> {
                    PinManagerChangeResult.Wrong(verifyResult.triesLeft)
                }

                is VerifyResult.Locked -> {
                    PinManagerChangeResult.Locked(verifyResult.remainingMs)
                }

                is VerifyResult.NoPin -> {
                    PinManagerChangeResult.NoPin
                }

                is VerifyResult.NotFound -> {
                    PinManagerChangeResult.NotFound
                }
            }
        }

        suspend fun removePin(
            profileId: Long,
            currentPin: String,
        ): PinManagerRemoveResult {
            val verifyResult = profilePinRepository.verify(profileId, currentPin)
            return when (verifyResult) {
                is VerifyResult.Correct -> {
                    profilePinRepository.removePin(profileId)
                    PinManagerRemoveResult.Removed
                }

                is VerifyResult.Wrong -> {
                    PinManagerRemoveResult.Wrong(verifyResult.triesLeft)
                }

                is VerifyResult.Locked -> {
                    PinManagerRemoveResult.Locked(verifyResult.remainingMs)
                }

                is VerifyResult.NoPin -> {
                    PinManagerRemoveResult.NoPin
                }

                is VerifyResult.NotFound -> {
                    PinManagerRemoveResult.NotFound
                }
            }
        }
    }
