package com.drift.stream.presentation.feature.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.R
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.data.profile.AvatarPhotoPipeline
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.CropState
import com.drift.stream.domain.profile.DeleteHost
import com.drift.stream.domain.profile.canDeleteProfile
import com.drift.stream.domain.profile.cropRect
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import com.drift.stream.domain.usecase.NameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

data class EditProfileUiState(
    val formState: ProfileFormState = ProfileFormState(name = "", colorIndex = 0, emoji = null),
    val profile: Profile? = null,
    val canDelete: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteErrorMessage: String? = null,
    @StringRes val deleteErrorRes: Int? = null,
    val closeScreen: Boolean = false,
    val activeProfileId: Long? = null,
    val cropBitmap: Bitmap? = null,
)

sealed interface EditProfileEffect {
    data class Done(
        val profileId: Long,
    ) : EditProfileEffect

    data class Deleted(
        val profileId: Long,
    ) : EditProfileEffect

    data object Close : EditProfileEffect
}

sealed interface EditProfileEvent {
    data object DeleteClicked : EditProfileEvent

    data object DeleteConfirmed : EditProfileEvent

    data object DeleteCancelled : EditProfileEvent

    data class PhotoSelected(
        val uri: Uri,
    ) : EditProfileEvent

    data class PhotoCropCompleted(
        val state: CropState,
        val windowPx: Int,
    ) : EditProfileEvent

    data object PhotoCropCancelled : EditProfileEvent

    data object DeleteErrorDismissed : EditProfileEvent
}

private const val CHOOSE_TIMEOUT_MS = 3_000L

@HiltViewModel
class EditProfileViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileHolder,
        private val avatarPhotoStore: AvatarPhotoStore,
        private val avatarPhotoPipeline: AvatarPhotoPipeline,
        private val savedStateHandle: SavedStateHandle,
        private val clock: Clock = Clock.systemUTC(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(EditProfileUiState())
        val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<EditProfileEffect>()
        val effects: SharedFlow<EditProfileEffect> = _effects.asSharedFlow()

        private var targetProfileId: Long? = null
        private var deleteHost: DeleteHost = DeleteHost.Picker
        private var isSubmitting = false
        private var isDeleting = false
        private var currentProfiles: List<Profile> = emptyList()

        init {
            val handleId = savedStateHandle.get<Long>("profileId")
            val handleHost =
                savedStateHandle.get<String>("host")?.let {
                    runCatching { DeleteHost.valueOf(it) }.getOrNull()
                }
            if (handleId != null && handleId > 0L) {
                initialize(handleId, handleHost ?: DeleteHost.Picker)
            }

            viewModelScope.launch {
                profileRepository.profiles.collect { profiles ->
                    currentProfiles = profiles
                    val id = targetProfileId ?: return@collect
                    if (profiles.isNotEmpty() && profiles.none { it.id == id }) {
                        _effects.emit(EditProfileEffect.Close)
                        _uiState.value = _uiState.value.copy(closeScreen = true)
                    }
                }
            }

            viewModelScope.launch {
                activeProfileHolder.activeProfileId.collect { activeId ->
                    val profile = _uiState.value.profile
                    if (profile != null) {
                        _uiState.value =
                            _uiState.value.copy(
                                canDelete = canDeleteProfile(profile, activeId, deleteHost),
                                activeProfileId = activeId,
                            )
                    }
                }
            }
        }

        fun initialize(
            profileId: Long,
            host: DeleteHost,
        ) {
            targetProfileId = profileId
            deleteHost = host
            viewModelScope.launch {
                val profile = profileRepository.get(profileId)
                if (profile == null) {
                    _effects.emit(EditProfileEffect.Close)
                    _uiState.value = _uiState.value.copy(closeScreen = true)
                    return@launch
                }
                val activeId = activeProfileHolder.activeProfileId.value
                val canDelete = canDeleteProfile(profile, activeId, host)
                _uiState.value =
                    _uiState.value.copy(
                        profile = profile,
                        formState = defaultStateForEdit(profile),
                        canDelete = canDelete,
                        activeProfileId = activeId,
                    )
            }
        }

        fun onEvent(event: ProfileFormEvent) {
            val id = targetProfileId ?: return
            val otherNames = otherNamesForEdit(currentProfiles, id)

            if (event is ProfileFormEvent.Submit) {
                if (!isSubmitting) {
                    val (newFormState, request) = reduceProfileForm(_uiState.value.formState, event, otherNames)
                    _uiState.value = _uiState.value.copy(formState = newFormState)
                    if (request != null) {
                        handleSave(request)
                    }
                }
            } else {
                val (newFormState, _) = reduceProfileForm(_uiState.value.formState, event, otherNames)
                _uiState.value = _uiState.value.copy(formState = newFormState)
            }
        }

        fun onEvent(event: EditProfileEvent) {
            when (event) {
                EditProfileEvent.DeleteClicked -> {
                    _uiState.value =
                        _uiState.value.copy(showDeleteDialog = true, deleteErrorMessage = null, deleteErrorRes = null)
                }

                EditProfileEvent.DeleteCancelled -> {
                    if (!isDeleting) {
                        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
                    }
                }

                EditProfileEvent.DeleteConfirmed -> {
                    handleDelete()
                }

                is EditProfileEvent.PhotoSelected -> {
                    handlePhotoSelected(event.uri)
                }

                is EditProfileEvent.PhotoCropCompleted -> {
                    handlePhotoCropCompleted(event.state, event.windowPx)
                }

                EditProfileEvent.PhotoCropCancelled -> {
                    _uiState.value = _uiState.value.copy(cropBitmap = null)
                }

                EditProfileEvent.DeleteErrorDismissed -> {
                    _uiState.value = _uiState.value.copy(deleteErrorMessage = null, deleteErrorRes = null)
                }
            }
        }

        private fun handleSave(request: SubmitRequest) {
            if (isSubmitting) return
            val currentProfile = _uiState.value.profile ?: return
            isSubmitting = true

            viewModelScope.launch {
                try {
                    val newPhotoStamp = preparePhotoForSave(currentProfile, request)
                    val updatedProfile =
                        currentProfile.copy(
                            name = request.name,
                            avatarColor = request.colorIndex,
                            avatarCustomColor = request.customColor,
                            avatarEmoji = request.emoji,
                            avatarPhotoStamp = newPhotoStamp,
                        )
                    val updateResult = profileRepository.update(updatedProfile)
                    handleUpdateResult(currentProfile, request, updateResult)
                } catch (_: Exception) {
                    _uiState.value =
                        _uiState.value.copy(
                            formState =
                                _uiState.value.formState.copy(
                                    saving = false,
                                    saveFailed = true,
                                ),
                        )
                } finally {
                    isSubmitting = false
                }
            }
        }

        private suspend fun preparePhotoForSave(
            profile: Profile,
            request: SubmitRequest,
        ): Long? {
            if (request.pendingPhotoPath != null) {
                val pendingFile = File(request.pendingPhotoPath)
                if (pendingFile.exists()) {
                    avatarPhotoStore.write(profile.id, pendingFile.readBytes())
                    return clock.millis()
                }
            }
            return if (profile.avatarPhotoStamp != null && request.avatarKind != AvatarKind.Photo) {
                null
            } else {
                profile.avatarPhotoStamp
            }
        }

        private suspend fun handleUpdateResult(
            profile: Profile,
            request: SubmitRequest,
            result: UpdateResult,
        ) {
            when (result) {
                is UpdateResult.Updated -> {
                    if (profile.avatarPhotoStamp != null && request.avatarKind != AvatarKind.Photo) {
                        avatarPhotoStore.delete(profile.id)
                    }
                    if (request.pendingPhotoPath != null) {
                        avatarPhotoPipeline.deletePending(request.pendingPhotoPath)
                    }
                    _effects.emit(EditProfileEffect.Done(profile.id))
                }

                is UpdateResult.InvalidName -> {
                    val error =
                        when (result.reason) {
                            NameResult.Blank -> NameError.Empty
                            NameResult.TooLong -> NameError.TooLong
                            NameResult.Duplicate -> NameError.Duplicate
                            else -> NameError.Invalid
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            formState =
                                _uiState.value.formState.copy(
                                    saving = false,
                                    nameError = error,
                                ),
                        )
                }

                is UpdateResult.NotFound -> {
                    _effects.emit(EditProfileEffect.Close)
                }
            }
        }

        private fun handleDelete() {
            if (isDeleting) return
            val currentProfile = _uiState.value.profile ?: return
            val activeId = activeProfileHolder.activeProfileId.value
            if (canDeleteProfile(currentProfile, activeId, deleteHost)) {
                isDeleting = true
                _uiState.value =
                    _uiState.value.copy(isDeleting = true, deleteErrorMessage = null, deleteErrorRes = null)

                viewModelScope.launch {
                    try {
                        if (deleteHost == DeleteHost.Picker &&
                            currentProfile.id == activeProfileHolder.activeProfileId.value
                        ) {
                            val owner =
                                currentProfiles.firstOrNull { it.isOwner }
                                    ?: profileRepository.ensureDefaultProfile()
                            if (activeProfileHolder.setActive(owner.id)) {
                                withTimeoutOrNull(CHOOSE_TIMEOUT_MS) {
                                    activeProfileHolder.activeProfileId.first { it == owner.id }
                                }
                            }
                        }
                        val result = profileRepository.delete(currentProfile.id)
                        handleDeleteResult(currentProfile.id, result)
                    } catch (_: Exception) {
                        _uiState.value =
                            _uiState.value.copy(
                                isDeleting = false,
                                deleteErrorMessage = "Couldn't delete. Try again.",
                                deleteErrorRes = R.string.profile_delete_error_failed,
                            )
                    } finally {
                        isDeleting = false
                    }
                }
            }
        }

        private suspend fun handleDeleteResult(
            profileId: Long,
            result: DeleteResult,
        ) {
            when (result) {
                is DeleteResult.Deleted -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                        )
                    _effects.emit(EditProfileEffect.Deleted(profileId))
                }

                is DeleteResult.RefusedActive -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                            deleteErrorMessage = "Switch to another profile first.",
                            deleteErrorRes = R.string.profile_delete_refused_active,
                        )
                }

                is DeleteResult.RefusedOwner, is DeleteResult.RefusedLast -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                            deleteErrorMessage = "This profile can't be deleted.",
                            deleteErrorRes = R.string.profile_delete_refused_owner,
                        )
                }

                is DeleteResult.NotFound -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                        )
                    _effects.emit(EditProfileEffect.Close)
                }
            }
        }

        private fun handlePhotoSelected(uri: Uri) {
            viewModelScope.launch {
                val bitmap = avatarPhotoPipeline.decode(uri)
                if (bitmap != null) {
                    _uiState.value = _uiState.value.copy(cropBitmap = bitmap)
                } else {
                    onEvent(ProfileFormEvent.PhotoError)
                }
            }
        }

        private fun handlePhotoCropCompleted(
            cropState: CropState,
            windowPx: Int,
        ) {
            val bitmap = _uiState.value.cropBitmap ?: return
            _uiState.value = _uiState.value.copy(cropBitmap = null)

            viewModelScope.launch {
                val rect = cropRect(cropState, bitmap.width, bitmap.height, windowPx)
                val path = avatarPhotoPipeline.cropAndSavePending(bitmap, rect)
                if (path != null) {
                    onEvent(ProfileFormEvent.PhotoCropped(path))
                } else {
                    onEvent(ProfileFormEvent.PhotoError)
                }
            }
        }

        public override fun onCleared() {
            super.onCleared()
            val pending = _uiState.value.formState.pendingPhotoPath
            if (pending != null) {
                avatarPhotoPipeline.deletePending(pending)
                try {
                    File(pending).delete()
                } catch (_: Exception) {
                }
            }
        }
    }
