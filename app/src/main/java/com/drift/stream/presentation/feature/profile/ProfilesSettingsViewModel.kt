package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileSettingsItem(
    val profile: Profile,
    val isOwner: Boolean,
    val isCurrent: Boolean,
)

data class ProfilesSettingsUiState(
    val profiles: List<ProfileSettingsItem> = emptyList(),
    val activeProfile: Profile? = null,
)

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class ProfilesSettingsViewModel
    @Inject
    constructor(
        profileRepository: ProfileRepository,
        activeProfileHolder: ActiveProfileHolder,
    ) : ViewModel() {
        val uiState: StateFlow<ProfilesSettingsUiState> =
            combine(
                profileRepository.profiles,
                activeProfileHolder.activeProfileId,
            ) { profiles, activeId ->
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
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ProfilesSettingsUiState(),
            )
    }
