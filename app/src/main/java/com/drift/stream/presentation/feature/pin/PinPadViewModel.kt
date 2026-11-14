package com.drift.stream.presentation.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.AppDataEraser
import com.drift.stream.domain.profile.PIN_LENGTH
import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinPurpose
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.repository.ProfilePinRepository
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.VerifyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PinTitleKind {
    EnterPin,
    CreatePin,
    ConfirmPin,
    OwnerResetPin,
    OwnerDeletePin,
    OwnerManagePin,
}

sealed interface ForgotState {
    data object OwnerErase : ForgotState

    data object OthersInfo : ForgotState

    data object EraseFailed : ForgotState
}

sealed interface PinMessage {
    data class Wrong(
        val triesLeft: Int,
    ) : PinMessage

    data class Locked(
        val remainingSeconds: Int,
    ) : PinMessage

    data object Mismatch : PinMessage
}

data class PinPadUiState(
    val titleKind: PinTitleKind = PinTitleKind.EnterPin,
    val profile: Profile? = null,
    val profileName: String? = null,
    val avatarColor: Int = 0,
    val avatarEmoji: String? = null,
    val avatarCustomColor: Int? = null,
    val avatarPhotoStamp: Long? = null,
    val profileId: Long? = null,
    val dotCount: Int = 0,
    val message: PinMessage? = null,
    val locked: Boolean = false,
    val busy: Boolean = false,
    val forgot: ForgotState? = null,
    val isVerify: Boolean = false,
    val holding: Boolean = false,
)

sealed interface PinPadEvent {
    data class DigitPressed(
        val digit: Char,
    ) : PinPadEvent

    data object Backspace : PinPadEvent

    data object Confirm : PinPadEvent

    data object Cancel : PinPadEvent

    data object ForgotPinPressed : PinPadEvent

    data object EraseConfirmed : PinPadEvent

    data object EraseDismissed : PinPadEvent

    data object ForgotInfoDismissed : PinPadEvent
}

private const val ONE_SECOND_MS = 1000L

private fun titleKindForVerify(purpose: PinPurpose): PinTitleKind =
    when (purpose) {
        PinPurpose.EnterProfile,
        PinPurpose.CurrentPinForChange,
        PinPurpose.CurrentPinForRemove,
        -> PinTitleKind.EnterPin

        PinPurpose.OwnerReset -> PinTitleKind.OwnerResetPin

        PinPurpose.DeleteProfile -> PinTitleKind.OwnerDeletePin

        PinPurpose.ManageProfiles -> PinTitleKind.OwnerManagePin
    }

@HiltViewModel
class PinPadViewModel
    @Inject
    constructor(
        private val profilePinRepository: ProfilePinRepository,
        private val profileRepository: ProfileRepository,
        private val challenges: PinChallenges,
        private val appDataEraser: AppDataEraser,
    ) : ViewModel() {
        constructor(
            profilePinRepository: ProfilePinRepository,
            profileRepository: ProfileRepository,
            challenges: PinChallenges,
        ) : this(
            profilePinRepository = profilePinRepository,
            profileRepository = profileRepository,
            challenges = challenges,
            appDataEraser =
                object : AppDataEraser {
                    override fun eraseEverything(): Boolean = false
                },
        )

        private val _uiState = MutableStateFlow(PinPadUiState())
        val uiState: StateFlow<PinPadUiState> = _uiState.asStateFlow()

        private var currentRequestId: Long = 0L
        private var currentRequest: PinRequest? = null
        private val typedDigits = StringBuilder()
        private var firstEntry: String? = null
        private var countdownJob: Job? = null
        private var isVerifying: Boolean = false

        fun initialize(pendingPin: PendingPin) {
            if (currentRequestId == pendingPin.id) return
            currentRequestId = pendingPin.id
            currentRequest = pendingPin.request
            typedDigits.clear()
            firstEntry = null
            countdownJob?.cancel()

            when (val req = pendingPin.request) {
                is PinRequest.Verify -> {
                    _uiState.update {
                        it.copy(
                            titleKind = titleKindForVerify(req.purpose),
                            profileId = req.profileId,
                            dotCount = 0,
                            message = null,
                            locked = false,
                            busy = false,
                            forgot = null,
                            isVerify = true,
                            holding = false,
                        )
                    }
                    viewModelScope.launch {
                        val profile = profileRepository.get(req.profileId)
                        _uiState.update {
                            it.copy(
                                profile = profile,
                                profileName = profile?.name,
                                avatarColor = profile?.avatarColor ?: 0,
                                avatarEmoji = profile?.avatarEmoji,
                                avatarCustomColor = profile?.avatarCustomColor,
                                avatarPhotoStamp = profile?.avatarPhotoStamp,
                            )
                        }
                        val rem = profilePinRepository.remainingLock(req.profileId)
                        if (rem > 0L) {
                            startCountdown(rem)
                        }
                    }
                }

                is PinRequest.Create -> {
                    _uiState.update {
                        it.copy(
                            titleKind = PinTitleKind.CreatePin,
                            profile = null,
                            profileName = null,
                            profileId = null,
                            dotCount = 0,
                            message = null,
                            locked = false,
                            busy = false,
                            forgot = null,
                            isVerify = false,
                            holding = false,
                        )
                    }
                }
            }
        }

        fun onEvent(event: PinPadEvent) {
            when (event) {
                is PinPadEvent.DigitPressed -> {
                    handleDigit(event.digit)
                }

                PinPadEvent.Backspace -> {
                    handleBackspace()
                }

                PinPadEvent.Confirm -> {
                    handleConfirm()
                }

                PinPadEvent.Cancel -> {
                    handleCancel()
                }

                PinPadEvent.ForgotPinPressed -> {
                    val req = currentRequest
                    if (req is PinRequest.Verify) {
                        val profile = _uiState.value.profile
                        val isOwner =
                            profile?.isOwner == true ||
                                req.purpose in
                                setOf(
                                    PinPurpose.OwnerReset,
                                    PinPurpose.DeleteProfile,
                                    PinPurpose.ManageProfiles,
                                )
                        _uiState.update {
                            it.copy(
                                forgot = if (isOwner) ForgotState.OwnerErase else ForgotState.OthersInfo,
                            )
                        }
                    }
                }

                PinPadEvent.EraseConfirmed -> {
                    val success = appDataEraser.eraseEverything()
                    if (!success) {
                        _uiState.update { it.copy(forgot = ForgotState.EraseFailed) }
                    }
                }

                PinPadEvent.EraseDismissed,
                PinPadEvent.ForgotInfoDismissed,
                -> {
                    _uiState.update { it.copy(forgot = null) }
                }
            }
        }

        private fun isActionBlocked(): Boolean = _uiState.value.busy || _uiState.value.locked || _uiState.value.holding

        private fun handleDigit(digit: Char) {
            if (isActionBlocked() || typedDigits.length >= PIN_LENGTH) return
            typedDigits.append(digit)
            val count = typedDigits.length
            _uiState.update { it.copy(dotCount = count, message = null) }
            if (count == PIN_LENGTH && currentRequest is PinRequest.Verify) {
                handleVerifySubmit()
            }
        }

        private fun handleBackspace() {
            if (isActionBlocked()) return
            if (typedDigits.isNotEmpty()) {
                typedDigits.deleteCharAt(typedDigits.length - 1)
                _uiState.update { it.copy(dotCount = typedDigits.length, message = null) }
            }
        }

        private fun handleConfirm() {
            if (isActionBlocked() || typedDigits.length < PIN_LENGTH) return
            if (currentRequest is PinRequest.Create) {
                handleCreateSubmit()
            }
        }

        private fun handleVerifySubmit() {
            if (isVerifying) return
            val req = currentRequest as? PinRequest.Verify ?: return
            val profileId = req.profileId
            val pin = typedDigits.toString()
            isVerifying = true
            _uiState.update { it.copy(busy = true) }
            viewModelScope.launch {
                val result = profilePinRepository.verify(profileId, pin)
                _uiState.update { it.copy(busy = false) }
                isVerifying = false
                processVerifyResult(result, pin)
            }
        }

        private fun processVerifyResult(
            result: VerifyResult,
            pin: String,
        ) {
            when (result) {
                is VerifyResult.Correct -> {
                    typedDigits.clear()
                    _uiState.update { it.copy(dotCount = 0) }
                    challenges.finish(currentRequestId, PinResult.Verified(pin))
                }

                is VerifyResult.Wrong -> {
                    typedDigits.clear()
                    _uiState.update {
                        it.copy(
                            dotCount = 0,
                            message = PinMessage.Wrong(result.triesLeft),
                        )
                    }
                }

                is VerifyResult.Locked -> {
                    typedDigits.clear()
                    startCountdown(result.remainingMs)
                }

                is VerifyResult.NoPin,
                is VerifyResult.NotFound,
                -> {
                    challenges.finish(currentRequestId, PinResult.Cancelled)
                }
            }
        }

        private fun handleCreateSubmit() {
            val pin = typedDigits.toString()
            typedDigits.clear()
            _uiState.update { it.copy(dotCount = 0) }

            if (firstEntry == null) {
                firstEntry = pin
                _uiState.update {
                    it.copy(
                        titleKind = PinTitleKind.ConfirmPin,
                        message = null,
                    )
                }
            } else {
                if (firstEntry == pin) {
                    challenges.finish(currentRequestId, PinResult.Created(pin))
                } else {
                    firstEntry = null
                    _uiState.update {
                        it.copy(
                            titleKind = PinTitleKind.CreatePin,
                            message = PinMessage.Mismatch,
                        )
                    }
                }
            }
        }

        private fun handleCancel() {
            challenges.finish(currentRequestId, PinResult.Cancelled)
        }

        private fun startCountdown(remainingMs: Long) {
            val totalSeconds = ((remainingMs + ONE_SECOND_MS - 1) / ONE_SECOND_MS).toInt()
            _uiState.update {
                it.copy(
                    dotCount = 0,
                    locked = true,
                    message = PinMessage.Locked(totalSeconds),
                )
            }
            countdownJob?.cancel()
            countdownJob =
                viewModelScope.launch {
                    var s = totalSeconds
                    while (s > 0) {
                        delay(ONE_SECOND_MS)
                        s--
                        if (s > 0) {
                            _uiState.update { it.copy(message = PinMessage.Locked(s)) }
                        }
                    }
                    _uiState.update { it.copy(locked = false, message = null) }
                }
        }
    }
