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
import com.drift.stream.domain.profile.OwnerResetPinResult
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinManagerChangeResult
import com.drift.stream.domain.profile.PinManagerRemoveResult
import com.drift.stream.domain.profile.PinManagerSetResult
import com.drift.stream.domain.profile.PinPurpose
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.profile.ProfilePinManager
import com.drift.stream.domain.profile.canDeleteProfile
import com.drift.stream.domain.profile.canEditProfile
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
    @StringRes val deleteErrorRes: Int? = null,
    val closeScreen: Boolean = false,
    val activeProfileId: Long? = null,
    val activeProfile: Profile? = null,
    val host: DeleteHost = DeleteHost.Picker,
    val cropBitmap: Bitmap? = null,
    val pinLockEnabled: Boolean = false,
    val pinActionError: Boolean = false,
    val showResetPinConfirm: Boolean = false,
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

    data object SetPinClicked : EditProfileEvent

    data object ChangePinClicked : EditProfileEvent

    data object RemovePinClicked : EditProfileEvent

    data object ResetPinClicked : EditProfileEvent

    data object ResetPinConfirmed : EditProfileEvent

    data object ResetPinDismissed : EditProfileEvent
}

class AvatarPhotoServices
    @Inject
    constructor(
        val store: AvatarPhotoStore,
        val pipeline: AvatarPhotoPipeline,
    )

class ProfilePinOps
    @Inject
    constructor(
        val manager: ProfilePinManager,
        val challenges: PinChallenges,
    )

private const val CHOOSE_TIMEOUT_MS = 3_000L

@HiltViewModel
class EditProfileViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileHolder,
        private val photoServices: AvatarPhotoServices,
        private val savedStateHandle: SavedStateHandle,
        private val pinOps: ProfilePinOps,
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
                    val activeId = activeProfileHolder.activeProfileId.value
                    val active = if (activeId != null) profiles.find { it.id == activeId } else null
                    val currentLoaded = _uiState.value.profile
                    if (currentLoaded != null && currentLoaded.id == id) {
                        val updated = profiles.find { it.id == id }
                        if (updated != null) {
                            _uiState.value =
                                _uiState.value.copy(
                                    profile = updated,
                                    activeProfile = active,
                                    pinLockEnabled = updated.isLocked,
                                )
                        }
                    }
                    if (profiles.isNotEmpty() && profiles.none { it.id == id }) {
                        _effects.emit(EditProfileEffect.Close)
                        _uiState.value = _uiState.value.copy(closeScreen = true)
                    }
                }
            }

            viewModelScope.launch {
                activeProfileHolder.activeProfileId.collect { activeId ->
                    val profile = _uiState.value.profile
                    val active = if (activeId != null) currentProfiles.find { it.id == activeId } else null
                    if (profile != null) {
                        _uiState.value =
                            _uiState.value.copy(
                                canDelete = canDeleteProfile(profile, activeId, deleteHost),
                                activeProfileId = activeId,
                                activeProfile = active,
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
            _uiState.value = _uiState.value.copy(host = host)
            viewModelScope.launch {
                val profile = profileRepository.get(profileId)
                if (profile == null) {
                    _effects.emit(EditProfileEffect.Close)
                    _uiState.value = _uiState.value.copy(closeScreen = true)
                    return@launch
                }
                val activeId = activeProfileHolder.activeProfileId.value
                val activeProfile =
                    if (activeId != null) {
                        currentProfiles.find { it.id == activeId }
                            ?: if (host == DeleteHost.Settings) {
                                profileRepository.get(activeId)
                            } else {
                                null
                            }
                    } else {
                        null
                    }
                if (host == DeleteHost.Settings) {
                    if (!canEditProfile(profile, activeProfile)) {
                        _effects.emit(EditProfileEffect.Close)
                        _uiState.value = _uiState.value.copy(closeScreen = true)
                        return@launch
                    }
                }
                val canDelete = canDeleteProfile(profile, activeId, host)
                _uiState.value =
                    _uiState.value.copy(
                        profile = profile,
                        formState = defaultStateForEdit(profile),
                        canDelete = canDelete,
                        activeProfileId = activeId,
                        activeProfile = activeProfile,
                        host = host,
                        pinLockEnabled = profile.isLocked,
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
                        _uiState.value.copy(showDeleteDialog = true, deleteErrorRes = null)
                }

                EditProfileEvent.DeleteCancelled -> {
                    if (!isDeleting) {
                        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
                    }
                }

                EditProfileEvent.DeleteConfirmed -> {
                    _uiState.value = _uiState.value.copy(showDeleteDialog = false)
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
                    _uiState.value = _uiState.value.copy(deleteErrorRes = null)
                }

                EditProfileEvent.SetPinClicked,
                EditProfileEvent.ChangePinClicked,
                EditProfileEvent.RemovePinClicked,
                -> {
                    handlePinAction(event)
                }

                EditProfileEvent.ResetPinClicked -> {
                    _uiState.value = _uiState.value.copy(showResetPinConfirm = true)
                }

                EditProfileEvent.ResetPinDismissed -> {
                    _uiState.value = _uiState.value.copy(showResetPinConfirm = false)
                }

                EditProfileEvent.ResetPinConfirmed -> {
                    _uiState.value = _uiState.value.copy(showResetPinConfirm = false)
                    handlePinAction(event)
                }
            }
        }

        private fun handlePinAction(event: EditProfileEvent) {
            val id = targetProfileId ?: return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(pinActionError = false)
                when (runPinAction(event, id, currentProfiles, pinOps)) {
                    PinActionResult.ResetSuccess -> {
                        _uiState.value = _uiState.value.copy(pinLockEnabled = false)
                    }

                    PinActionResult.Failed -> {
                        _uiState.value = _uiState.value.copy(pinActionError = true)
                    }

                    PinActionResult.Success,
                    PinActionResult.NoOp,
                    -> {}
                }
            }
        }

        private fun handleSave(request: SubmitRequest) {
            if (isSubmitting) return
            val currentProfile = _uiState.value.profile ?: return
            isSubmitting = true

            viewModelScope.launch {
                try {
                    val newPhotoStamp = preparePhotoForSave(currentProfile, request, photoServices.store, clock)
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

        private suspend fun handleUpdateResult(
            profile: Profile,
            request: SubmitRequest,
            result: UpdateResult,
        ) {
            when (result) {
                is UpdateResult.Updated -> {
                    if (profile.avatarPhotoStamp != null && request.avatarKind != AvatarKind.Photo) {
                        photoServices.store.delete(profile.id)
                    }
                    if (request.pendingPhotoPath != null) {
                        photoServices.pipeline.deletePending(request.pendingPhotoPath)
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
                    _uiState.value.copy(isDeleting = true, deleteErrorRes = null)

                viewModelScope.launch {
                    try {
                        val owner = currentProfiles.find { it.isOwner }
                        if (owner?.isLocked == true) {
                            val pinResult =
                                pinOps.challenges.ask(PinRequest.Verify(owner.id, PinPurpose.DeleteProfile))
                            if (pinResult !is PinResult.Verified) {
                                isDeleting = false
                                _uiState.value = _uiState.value.copy(isDeleting = false)
                                return@launch
                            }
                        }
                        if (deleteHost == DeleteHost.Picker &&
                            currentProfile.id == activeProfileHolder.activeProfileId.value
                        ) {
                            val fallbackOwner =
                                owner ?: profileRepository.ensureDefaultProfile()
                            if (activeProfileHolder.setActive(fallbackOwner.id)) {
                                withTimeoutOrNull(CHOOSE_TIMEOUT_MS) {
                                    activeProfileHolder.activeProfileId.first { it == fallbackOwner.id }
                                }
                            }
                        }
                        val result = profileRepository.delete(currentProfile.id)
                        handleDeleteResult(currentProfile.id, result)
                    } catch (_: Exception) {
                        _uiState.value =
                            _uiState.value.copy(
                                isDeleting = false,
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
                            deleteErrorRes = R.string.profile_delete_refused_active,
                        )
                }

                is DeleteResult.RefusedOwner, is DeleteResult.RefusedLast -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
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
                val bitmap = photoServices.pipeline.decode(uri)
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
                val path = photoServices.pipeline.cropAndSavePending(bitmap, rect)
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
                photoServices.pipeline.deletePending(pending)
                try {
                    File(pending).delete()
                } catch (_: Exception) {
                }
            }
        }
    }

private suspend fun preparePhotoForSave(
    profile: Profile,
    request: SubmitRequest,
    avatarPhotoStore: AvatarPhotoStore,
    clock: Clock,
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

private enum class PinActionResult {
    Success,
    ResetSuccess,
    Failed,
    NoOp,
}

private suspend fun runSetPin(
    id: Long,
    pinOps: ProfilePinOps,
): PinActionResult {
    val result = pinOps.challenges.ask(PinRequest.Create)
    if (result !is PinResult.Created) return PinActionResult.NoOp
    return if (pinOps.manager.setPin(id, result.pin) is PinManagerSetResult.Set) {
        PinActionResult.Success
    } else {
        PinActionResult.Failed
    }
}

private suspend fun runChangePin(
    id: Long,
    pinOps: ProfilePinOps,
): PinActionResult {
    val verifyResult = pinOps.challenges.ask(PinRequest.Verify(id, PinPurpose.CurrentPinForChange))
    val createResult =
        if (verifyResult is PinResult.Verified) {
            pinOps.challenges.ask(PinRequest.Create)
        } else {
            null
        }
    if (verifyResult !is PinResult.Verified || createResult !is PinResult.Created) {
        return PinActionResult.NoOp
    }
    return if (pinOps.manager.changePin(id, verifyResult.pin, createResult.pin) is PinManagerChangeResult.Changed) {
        PinActionResult.Success
    } else {
        PinActionResult.Failed
    }
}

private suspend fun runRemovePin(
    id: Long,
    pinOps: ProfilePinOps,
): PinActionResult {
    val verifyResult = pinOps.challenges.ask(PinRequest.Verify(id, PinPurpose.CurrentPinForRemove))
    if (verifyResult !is PinResult.Verified) return PinActionResult.NoOp
    return if (pinOps.manager.removePin(id, verifyResult.pin) is PinManagerRemoveResult.Removed) {
        PinActionResult.Success
    } else {
        PinActionResult.Failed
    }
}

private suspend fun runResetPin(
    id: Long,
    profiles: List<Profile>,
    pinOps: ProfilePinOps,
): PinActionResult {
    val owner = profiles.find { it.isOwner }
    val verifyResult =
        if (owner?.isLocked == true) {
            pinOps.challenges.ask(PinRequest.Verify(owner.id, PinPurpose.OwnerReset))
        } else {
            null
        }
    if (owner == null || (owner.isLocked && verifyResult !is PinResult.Verified)) {
        return PinActionResult.NoOp
    }
    val ownerPin = (verifyResult as? PinResult.Verified)?.pin
    return if (pinOps.manager.ownerResetPin(owner.id, ownerPin, id) is OwnerResetPinResult.Removed) {
        PinActionResult.ResetSuccess
    } else {
        PinActionResult.Failed
    }
}

private suspend fun runPinAction(
    event: EditProfileEvent,
    id: Long,
    profiles: List<Profile>,
    pinOps: ProfilePinOps,
): PinActionResult =
    when (event) {
        EditProfileEvent.SetPinClicked -> runSetPin(id, pinOps)
        EditProfileEvent.ChangePinClicked -> runChangePin(id, pinOps)
        EditProfileEvent.RemovePinClicked -> runRemovePin(id, pinOps)
        EditProfileEvent.ResetPinConfirmed -> runResetPin(id, profiles, pinOps)
        else -> PinActionResult.NoOp
    }
