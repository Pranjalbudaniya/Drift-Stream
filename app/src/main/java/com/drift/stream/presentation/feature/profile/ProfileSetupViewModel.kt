package com.drift.stream.presentation.feature.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.profile.AvatarPhotoPipeline
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.profile.CropRect
import com.drift.stream.domain.profile.CropState
import com.drift.stream.domain.profile.cropRect
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
import java.io.File
import java.io.IOException
import java.time.Clock
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
        private val avatarPhotoStore: AvatarPhotoStore,
        private val avatarPhotoPipeline: AvatarPhotoPipeline,
        private val clock: Clock,
    ) : ViewModel() {
        constructor(
            profileRepository: ProfileRepository,
            savedStateHandle: SavedStateHandle,
        ) : this(
            profileRepository = profileRepository,
            savedStateHandle = savedStateHandle,
            avatarPhotoStore =
                object : AvatarPhotoStore {
                    override fun fileOf(profileId: Long) = File("")

                    override suspend fun write(
                        profileId: Long,
                        bytes: ByteArray,
                    ) = Unit

                    override suspend fun delete(profileId: Long) = Unit
                },
            avatarPhotoPipeline =
                object : AvatarPhotoPipeline {
                    override suspend fun decode(uri: Uri) = null

                    override suspend fun cropAndSavePending(
                        bitmap: Bitmap,
                        cropRect: CropRect,
                    ) = null

                    override suspend fun readBytes(path: String) =
                        try {
                            File(path).readBytes()
                        } catch (_: IOException) {
                            null
                        }

                    override fun deletePending(path: String?) {
                        path?.let { File(it).delete() }
                    }
                },
            clock = Clock.systemUTC(),
        )

        constructor(
            profileRepository: ProfileRepository,
            savedStateHandle: SavedStateHandle,
            avatarPhotoStore: AvatarPhotoStore,
            clock: Clock,
        ) : this(
            profileRepository = profileRepository,
            savedStateHandle = savedStateHandle,
            avatarPhotoStore = avatarPhotoStore,
            avatarPhotoPipeline =
                object : AvatarPhotoPipeline {
                    override suspend fun decode(uri: Uri) = null

                    override suspend fun cropAndSavePending(
                        bitmap: Bitmap,
                        cropRect: CropRect,
                    ) = null

                    override suspend fun readBytes(path: String) =
                        try {
                            File(path).readBytes()
                        } catch (_: IOException) {
                            null
                        }

                    override fun deletePending(path: String?) {
                        path?.let { File(it).delete() }
                    }
                },
            clock = clock,
        )

        private var resolvedOwnerId: Long = savedStateHandle.get<Long>(KEY_OWNER_ID) ?: 1L
        private var existingProfiles: List<Profile> = emptyList()
        private var isInitialized: Boolean = false

        private val _effects = MutableSharedFlow<SetupEffect>(extraBufferCapacity = 1)
        val effects: SharedFlow<SetupEffect> = _effects.asSharedFlow()

        private val _croppingBitmap = MutableStateFlow<Bitmap?>(null)
        val croppingBitmap: StateFlow<Bitmap?> = _croppingBitmap.asStateFlow()

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

        fun onPhotoPicked(uri: Uri) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(photoError = false)
                val bitmap = avatarPhotoPipeline.decode(uri)
                if (bitmap != null) {
                    _croppingBitmap.value = bitmap
                } else {
                    _uiState.value = _uiState.value.copy(photoError = true)
                }
            }
        }

        fun onCropDone(
            cropState: CropState,
            windowSize: Int,
        ) {
            val bitmap = _croppingBitmap.value ?: return
            _croppingBitmap.value = null
            viewModelScope.launch {
                val rect = cropRect(cropState, bitmap.width, bitmap.height, windowSize)
                val pendingPath = avatarPhotoPipeline.cropAndSavePending(bitmap, rect)
                if (pendingPath != null) {
                    onEvent(ProfileFormEvent.PhotoCropped(pendingPath))
                } else {
                    onEvent(ProfileFormEvent.PhotoError)
                }
            }
        }

        fun onCropCancel() {
            _croppingBitmap.value = null
        }

        fun onEvent(event: ProfileFormEvent) {
            val otherNames = existingProfiles.filter { it.id != resolvedOwnerId }.map { it.name }
            val (nextState, request) = reduceProfileForm(_uiState.value, event, otherNames)
            _uiState.value = nextState
            persistState(nextState)

            if (request != null) {
                handleSubmit(request, otherNames)
            }
        }

        private fun handleSubmit(
            request: SubmitRequest,
            otherNames: Collection<String>,
        ) {
            viewModelScope.launch {
                val pendingPath = request.pendingPhotoPath
                var photoStamp: Long? = null

                if (pendingPath != null) {
                    val bytes = avatarPhotoPipeline.readBytes(pendingPath)
                    if (bytes == null) {
                        failSubmit(otherNames)
                        return@launch
                    }
                    try {
                        avatarPhotoStore.write(resolvedOwnerId, bytes)
                        photoStamp = clock.millis()
                    } catch (_: IOException) {
                        failSubmit(otherNames)
                        return@launch
                    }
                }

                executeCompleteSetup(request, photoStamp, pendingPath, otherNames)
            }
        }

        private suspend fun executeCompleteSetup(
            request: SubmitRequest,
            photoStamp: Long?,
            pendingPath: String?,
            otherNames: Collection<String>,
        ) {
            val result =
                profileRepository.completeSetup(
                    id = resolvedOwnerId,
                    draft =
                        ProfileDraft(
                            name = request.name,
                            avatarColor = request.colorIndex,
                            avatarEmoji = request.emoji,
                            avatarCustomColor = request.customColor,
                            avatarPhotoStamp = photoStamp,
                        ),
                )
            when (result) {
                is UpdateResult.Updated -> {
                    if (pendingPath != null) {
                        avatarPhotoPipeline.deletePending(pendingPath)
                    }
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

        private fun failSubmit(otherNames: Collection<String>) {
            val (failedState, _) =
                reduceProfileForm(
                    _uiState.value,
                    ProfileFormEvent.PhotoError,
                    otherNames,
                )
            _uiState.value = failedState.copy(saving = false)
            persistState(_uiState.value)
        }

        public override fun onCleared() {
            super.onCleared()
            avatarPhotoPipeline.deletePending(_uiState.value.pendingPhotoPath)
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
