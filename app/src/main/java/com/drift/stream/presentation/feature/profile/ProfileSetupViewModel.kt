package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SetupEffect {
    data class Done(
        val ownerId: Long,
    ) : SetupEffect
}

@HiltViewModel
class ProfileSetupViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private var resolvedOwnerId: Long = savedStateHandle.get<Long>(KEY_OWNER_ID) ?: 1L
        private var existingProfiles: List<Profile> = emptyList()
        private var isInitialized: Boolean = false

        private val _effects = MutableSharedFlow<SetupEffect>(extraBufferCapacity = 1)
        val effects: SharedFlow<SetupEffect> = _effects.asSharedFlow()

        private val _uiState: MutableStateFlow<ProfileFormState>

        init {
            val savedName: String? = savedStateHandle[KEY_NAME]
            val savedColor: Int? = savedStateHandle[KEY_COLOR]
            val savedEmoji: String? = savedStateHandle[KEY_EMOJI]
            val savedCustomColor: Int? = savedStateHandle[KEY_CUSTOM_COLOR]
            val savedPendingPhoto: String? = savedStateHandle[KEY_PENDING_PHOTO]

            if (savedName != null) {
                isInitialized = true
                _uiState =
                    MutableStateFlow(
                        ProfileFormState(
                            name = savedName,
                            colorIndex = savedColor ?: 0,
                            emoji = savedEmoji,
                            customColor = savedCustomColor,
                            pendingPhotoPath = savedPendingPhoto,
                        ),
                    )
            } else {
                _uiState = MutableStateFlow(ProfileFormState(name = "Me", colorIndex = 0, emoji = null))
            }

            viewModelScope.launch {
                profileRepository.profiles.collect { list ->
                    existingProfiles = list
                    val owner = list.firstOrNull { it.isOwner }
                    if (owner != null) {
                        resolvedOwnerId = owner.id
                        savedStateHandle[KEY_OWNER_ID] = owner.id
                        if (!isInitialized) {
                            isInitialized = true
                            val state = defaultStateForSetup(owner)
                            _uiState.value = state
                            persistState(state)
                        }
                    }
                }
            }
        }

        val uiState: StateFlow<ProfileFormState> = _uiState.asStateFlow()

        fun onEvent(event: ProfileFormEvent) {
            val otherNames = existingProfiles.filter { it.id != resolvedOwnerId }.map { it.name }
            val (nextState, request) = reduceProfileForm(_uiState.value, event, otherNames)
            _uiState.value = nextState
            persistState(nextState)

            if (request != null) {
                viewModelScope.launch {
                    val result =
                        profileRepository.completeSetup(
                            id = resolvedOwnerId,
                            name = request.name,
                            avatarColor = request.colorIndex,
                            avatarEmoji = request.emoji,
                            avatarCustomColor = request.customColor,
                            avatarPhotoStamp = null,
                        )
                    when (result) {
                        is UpdateResult.Updated -> {
                            _effects.emit(SetupEffect.Done(resolvedOwnerId))
                        }

                        else -> {
                            val (failedState, _) =
                                reduceProfileForm(
                                    _uiState.value,
                                    ProfileFormEvent.SaveFailed,
                                    otherNames,
                                )
                            _uiState.value = failedState
                            persistState(failedState)
                        }
                    }
                }
            }
        }

        private fun persistState(state: ProfileFormState) {
            savedStateHandle[KEY_NAME] = state.name
            savedStateHandle[KEY_COLOR] = state.colorIndex
            savedStateHandle[KEY_EMOJI] = state.emoji
            savedStateHandle[KEY_CUSTOM_COLOR] = state.customColor
            savedStateHandle[KEY_PENDING_PHOTO] = state.pendingPhotoPath
        }

        companion object {
            const val KEY_OWNER_ID = "profile_owner_id"
            const val KEY_NAME = "profile_name"
            const val KEY_COLOR = "profile_color"
            const val KEY_EMOJI = "profile_emoji"
            const val KEY_CUSTOM_COLOR = "profile_custom_color"
            const val KEY_PENDING_PHOTO = "profile_pending_photo"
        }
    }
