package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddProfileEffect {
    data class Done(
        val profileId: Long,
    ) : AddProfileEffect
}

@HiltViewModel
class AddProfileViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private var existingProfiles: List<Profile> = emptyList()
        private var isInitialized: Boolean = false

        private val _effects = MutableSharedFlow<AddProfileEffect>(extraBufferCapacity = 1)
        val effects: SharedFlow<AddProfileEffect> = _effects.asSharedFlow()

        private val _uiState: MutableStateFlow<ProfileFormState>

        init {
            val savedName: String? = savedStateHandle[KEY_NAME]
            val savedColor: Int? = savedStateHandle[KEY_COLOR]
            val savedEmoji: String? = savedStateHandle[KEY_EMOJI]

            if (savedName != null) {
                isInitialized = true
                _uiState =
                    MutableStateFlow(
                        ProfileFormState(
                            name = savedName,
                            colorIndex = savedColor ?: 0,
                            emoji = savedEmoji,
                        ),
                    )
            } else {
                _uiState = MutableStateFlow(ProfileFormState(name = "", colorIndex = 0, emoji = null))
            }

            viewModelScope.launch {
                profileRepository.profiles.collect { list ->
                    existingProfiles = list
                    if (!isInitialized) {
                        isInitialized = true
                        val state = defaultStateForAdd(list)
                        _uiState.value = state
                        persistState(state)
                    }
                }
            }
        }

        val uiState: StateFlow<ProfileFormState> = _uiState.asStateFlow()

        fun onEvent(event: ProfileFormEvent) {
            val otherNames = existingProfiles.map { it.name }
            val (nextState, request) = reduceProfileForm(_uiState.value, event, otherNames)
            _uiState.value = nextState
            persistState(nextState)

            if (request != null) {
                viewModelScope.launch {
                    val result =
                        profileRepository.create(
                            name = request.name,
                            avatarColor = request.colorIndex,
                            avatarEmoji = request.emoji,
                        )
                    when (result) {
                        is CreateResult.Created -> {
                            _effects.emit(AddProfileEffect.Done(result.profile.id))
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

        fun reset() {
            val state = defaultStateForAdd(existingProfiles)
            _uiState.value = state
            persistState(state)
        }

        private fun persistState(state: ProfileFormState) {
            savedStateHandle[KEY_NAME] = state.name
            savedStateHandle[KEY_COLOR] = state.colorIndex
            savedStateHandle[KEY_EMOJI] = state.emoji
        }

        companion object {
            const val KEY_NAME = "add_profile_name"
            const val KEY_COLOR = "add_profile_color"
            const val KEY_EMOJI = "add_profile_emoji"
        }
    }
