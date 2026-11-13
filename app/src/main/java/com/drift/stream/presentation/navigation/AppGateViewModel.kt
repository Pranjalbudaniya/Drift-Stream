package com.drift.stream.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.CHOOSE_TIMEOUT_MS
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinPurpose
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.profile.StartGate
import com.drift.stream.domain.profile.decideStartGate
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class GateUiState(
    val gate: StartGate = StartGate.Resolving,
    val profiles: List<Profile> = emptyList(),
    val chooseFailed: Boolean = false,
    val isChoosing: Boolean = false,
)

@HiltViewModel
class AppGateViewModel
    @Inject
    constructor(
        profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileHolder,
        appStateRepository: AppStateRepository,
        private val pinChallenges: PinChallenges,
    ) : ViewModel() {
        private val choseThisSession = MutableStateFlow(false)
        private val isChoosing = MutableStateFlow(false)
        private val chooseFailed = MutableStateFlow(false)
        private var hasEnteredSession = false

        private data class GateInputs(
            val profiles: List<Profile>,
            val activeProfileId: Long?,
            val alwaysShowPicker: Boolean,
            val choseThisSession: Boolean,
        )

        private val gateInputsFlow =
            combine(
                profileRepository.profiles,
                activeProfileHolder.activeProfileId,
                appStateRepository.alwaysShowPicker,
                choseThisSession,
            ) { profiles, activeId, alwaysShow, chose ->
                GateInputs(profiles, activeId, alwaysShow, chose)
            }

        val uiState: StateFlow<GateUiState> =
            combine(
                gateInputsFlow,
                isChoosing,
                chooseFailed,
            ) { inputs, choosing, failed ->
                val gate =
                    if (hasEnteredSession) {
                        StartGate.Enter
                    } else {
                        val computed =
                            decideStartGate(
                                profiles = inputs.profiles,
                                activeProfileId = inputs.activeProfileId,
                                alwaysShowPicker = inputs.alwaysShowPicker,
                                choseThisSession = inputs.choseThisSession,
                            )
                        if (computed is StartGate.Enter) {
                            hasEnteredSession = true
                        }
                        computed
                    }
                GateUiState(
                    gate = gate,
                    profiles = inputs.profiles,
                    chooseFailed = failed,
                    isChoosing = choosing,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = GateUiState(gate = StartGate.Resolving),
            )

        fun onProfileChosen(id: Long) {
            if (isChoosing.value) return
            val profile = uiState.value.profiles.firstOrNull { it.id == id }
            isChoosing.value = true
            chooseFailed.value = false
            viewModelScope.launch {
                try {
                    if (profile?.isLocked == true) {
                        val result = pinChallenges.ask(PinRequest.Verify(id, PinPurpose.EnterProfile))
                        if (result !is PinResult.Verified) {
                            return@launch
                        }
                    }
                    val ok = activeProfileHolder.setActive(id)
                    if (ok) {
                        withTimeout(CHOOSE_TIMEOUT_MS) {
                            activeProfileHolder.activeProfileId.first { it == id }
                        }
                        choseThisSession.value = true
                    } else {
                        chooseFailed.value = true
                    }
                } catch (ignored: TimeoutCancellationException) {
                    chooseFailed.value = true
                } catch (ignored: IllegalStateException) {
                    chooseFailed.value = true
                } finally {
                    isChoosing.value = false
                }
            }
        }

        fun onSetupCompleted(ownerId: Long) {
            viewModelScope.launch {
                if (activeProfileHolder.activeProfileId.value != ownerId) {
                    activeProfileHolder.setActive(ownerId)
                }
                choseThisSession.value = true
            }
        }

        fun onChooseErrorShown() {
            chooseFailed.value = false
        }
    }
