package com.drift.stream.presentation.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.PIN_MAX_LENGTH
import com.drift.stream.domain.profile.PIN_MIN_LENGTH
import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.domain.profile.PinChallenges
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
)

sealed interface PinPadEvent {
    data class DigitPressed(
        val digit: Char,
    ) : PinPadEvent

    data object Backspace : PinPadEvent

    data object Confirm : PinPadEvent

    data object Cancel : PinPadEvent
}

private const val ONE_SECOND_MS = 1000L

@HiltViewModel
class PinPadViewModel
    @Inject
    constructor(
        private val profilePinRepository: ProfilePinRepository,
        private val profileRepository: ProfileRepository,
        private val challenges: PinChallenges,
    ) : ViewModel() {
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
                            titleKind = PinTitleKind.EnterPin,
                            profileId = req.profileId,
                            dotCount = 0,
                            message = null,
                            locked = false,
                            busy = false,
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
                            profileName = null,
                            profileId = null,
                            dotCount = 0,
                            message = null,
                            locked = false,
                            busy = false,
                        )
                    }
                }
            }
        }

        fun onEvent(event: PinPadEvent) {
            when (event) {
                is PinPadEvent.DigitPressed -> handleDigit(event.digit)
                is PinPadEvent.Backspace -> handleBackspace()
                is PinPadEvent.Confirm -> handleConfirm()
                is PinPadEvent.Cancel -> handleCancel()
            }
        }

        private fun handleDigit(digit: Char) {
            if (_uiState.value.busy || _uiState.value.locked || typedDigits.length >= PIN_MAX_LENGTH) return
            typedDigits.append(digit)
            val newDotCount = typedDigits.length
            _uiState.update {
                it.copy(
                    dotCount = newDotCount,
                    message = if (it.message !is PinMessage.Locked) null else it.message,
                )
            }
            if (newDotCount == PIN_MAX_LENGTH) {
                handleConfirm()
            }
        }

        private fun handleBackspace() {
            if (_uiState.value.busy || _uiState.value.locked || typedDigits.isEmpty()) return
            typedDigits.deleteCharAt(typedDigits.length - 1)
            _uiState.update { it.copy(dotCount = typedDigits.length) }
        }

        private fun handleConfirm() {
            if (_uiState.value.busy || _uiState.value.locked || typedDigits.length < PIN_MIN_LENGTH) return
            when (currentRequest) {
                is PinRequest.Verify -> handleVerifySubmit()
                is PinRequest.Create -> handleCreateSubmit()
                null -> Unit
            }
        }

        private fun handleVerifySubmit() {
            if (isVerifying) return
            val profileId = _uiState.value.profileId ?: return
            isVerifying = true
            _uiState.update { it.copy(busy = true) }
            val pin = typedDigits.toString()

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

                is VerifyResult.NoPin, is VerifyResult.NotFound -> {
                    challenges.finish(currentRequestId, PinResult.Cancelled)
                }
            }
        }

        private fun handleCreateSubmit() {
            val entry = typedDigits.toString()
            val first = firstEntry
            if (first == null) {
                firstEntry = entry
                typedDigits.clear()
                _uiState.update {
                    it.copy(
                        titleKind = PinTitleKind.ConfirmPin,
                        dotCount = 0,
                        message = null,
                    )
                }
            } else if (entry == first) {
                typedDigits.clear()
                _uiState.update { it.copy(dotCount = 0) }
                challenges.finish(currentRequestId, PinResult.Created(entry))
            } else {
                firstEntry = null
                typedDigits.clear()
                _uiState.update {
                    it.copy(
                        titleKind = PinTitleKind.CreatePin,
                        dotCount = 0,
                        message = PinMessage.Mismatch,
                    )
                }
            }
        }

        private fun handleCancel() {
            challenges.finish(currentRequestId, PinResult.Cancelled)
        }

        private fun startCountdown(remainingMs: Long) {
            val seconds = ((remainingMs + 999) / ONE_SECOND_MS).toInt()
            _uiState.update {
                it.copy(
                    dotCount = 0,
                    locked = true,
                    message = PinMessage.Locked(seconds),
                )
            }
            countdownJob?.cancel()
            countdownJob =
                viewModelScope.launch {
                    var rem = seconds
                    while (rem > 0) {
                        delay(ONE_SECOND_MS)
                        rem--
                        if (rem > 0) {
                            _uiState.update { it.copy(message = PinMessage.Locked(rem)) }
                        } else {
                            _uiState.update { it.copy(locked = false, message = null) }
                        }
                    }
                }
        }
    }
