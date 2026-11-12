package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.CHOOSE_TIMEOUT_MS
import com.drift.stream.domain.profile.canManageProfiles
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class ProfileSwitcherUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val canAdd: Boolean = false,
    val switching: Boolean = false,
    val error: Boolean = false,
)

sealed interface ProfileSwitcherEvent {
    data class ProfileTapped(
        val id: Long,
    ) : ProfileSwitcherEvent

    data object AddProfileTapped : ProfileSwitcherEvent

    data object ErrorShown : ProfileSwitcherEvent
}

sealed interface ProfileSwitcherEffect {
    data object Switched : ProfileSwitcherEffect

    data object OpenAddProfile : ProfileSwitcherEffect

    data object Close : ProfileSwitcherEffect
}

@HiltViewModel
class ProfileSwitcherViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileHolder,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProfileSwitcherUiState())
        val uiState: StateFlow<ProfileSwitcherUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<ProfileSwitcherEffect>()
        val effects: SharedFlow<ProfileSwitcherEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                combine(
                    profileRepository.profiles,
                    activeProfileHolder.activeProfileId,
                ) { profiles, activeId ->
                    val active = profiles.find { it.id == activeId }
                    Pair(profiles, active)
                }.collect { (profiles, active) ->
                    _uiState.update { current ->
                        current.copy(
                            profiles = profiles,
                            activeProfile = active,
                            canAdd = canManageProfiles(active),
                        )
                    }
                }
            }
        }

        fun onEvent(event: ProfileSwitcherEvent) {
            when (event) {
                is ProfileSwitcherEvent.ProfileTapped -> {
                    handleProfileTapped(event.id)
                }

                ProfileSwitcherEvent.AddProfileTapped -> {
                    viewModelScope.launch {
                        _effects.emit(ProfileSwitcherEffect.OpenAddProfile)
                    }
                }

                ProfileSwitcherEvent.ErrorShown -> {
                    _uiState.update { it.copy(error = false) }
                }
            }
        }

        private fun handleProfileTapped(id: Long) {
            if (_uiState.value.switching) return

            if (id == _uiState.value.activeProfile?.id) {
                viewModelScope.launch {
                    _effects.emit(ProfileSwitcherEffect.Close)
                }
                return
            }

            _uiState.update { it.copy(switching = true, error = false) }

            viewModelScope.launch {
                try {
                    val success = activeProfileHolder.setActive(id)
                    if (!success) {
                        _uiState.update { it.copy(switching = false, error = true) }
                        return@launch
                    }
                    val matched =
                        withTimeoutOrNull(CHOOSE_TIMEOUT_MS) {
                            activeProfileHolder.activeProfileId.first { it == id }
                        }
                    if (matched == null) {
                        _uiState.update { it.copy(switching = false, error = true) }
                        return@launch
                    }
                    _uiState.update { it.copy(switching = false, error = false) }
                    _effects.emit(ProfileSwitcherEffect.Switched)
                } catch (_: Exception) {
                    _uiState.update { it.copy(switching = false, error = true) }
                }
            }
        }
    }
