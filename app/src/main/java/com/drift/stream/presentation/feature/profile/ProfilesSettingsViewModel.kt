package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.canManageProfiles
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSettingsItem(
    val profile: Profile,
    val isOwner: Boolean,
    val isCurrent: Boolean,
)

data class ProfilesSettingsUiState(
    val profiles: List<ProfileSettingsItem> = emptyList(),
    val activeProfile: Profile? = null,
    val canManage: Boolean = false,
    val alwaysShowPicker: Boolean = false,
)

sealed interface ProfilesSettingsEvent {
    data class SetAlwaysShowPicker(
        val enabled: Boolean,
    ) : ProfilesSettingsEvent
}

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class ProfilesSettingsViewModel
    @Inject
    constructor(
        profileRepository: ProfileRepository,
        activeProfileHolder: ActiveProfileHolder,
        private val appStateRepository: AppStateRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ProfilesSettingsUiState> =
            combine(
                profileRepository.profiles,
                activeProfileHolder.activeProfileId,
                appStateRepository.alwaysShowPicker,
            ) { profiles, activeId, alwaysShowPicker ->
                val items =
                    profiles.map { profile ->
                        ProfileSettingsItem(
                            profile = profile,
                            isOwner = profile.isOwner,
                            isCurrent = profile.id == activeId,
                        )
                    }
                val activeProfile = profiles.firstOrNull { it.id == activeId }
                ProfilesSettingsUiState(
                    profiles = items,
                    activeProfile = activeProfile,
                    canManage = canManageProfiles(activeProfile),
                    alwaysShowPicker = alwaysShowPicker,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ProfilesSettingsUiState(),
            )

        fun onEvent(event: ProfilesSettingsEvent) {
            when (event) {
                is ProfilesSettingsEvent.SetAlwaysShowPicker -> {
                    viewModelScope.launch {
                        appStateRepository.setAlwaysShowPicker(event.enabled)
                    }
                }
            }
        }
    }
