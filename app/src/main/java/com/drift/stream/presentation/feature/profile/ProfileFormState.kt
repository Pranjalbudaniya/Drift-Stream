package com.drift.stream.presentation.feature.profile

import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.NameCheck
import com.drift.stream.domain.profile.PROFILE_NAME_MAX_LENGTH
import com.drift.stream.domain.profile.forceOpaque
import com.drift.stream.domain.profile.isValidAvatarEmoji
import com.drift.stream.domain.profile.nextFreeAvatarColor
import com.drift.stream.domain.profile.normalizeAvatarColor
import com.drift.stream.domain.profile.validateProfileName

data class ProfileFormState(
    val name: String,
    val colorIndex: Int,
    val emoji: String?,
    val customColor: Int? = null,
    val pendingPhotoPath: String? = null,
    val existingPhotoStamp: Long? = null,
    val existingPhotoProfileId: Long? = null,
    val nameError: NameError? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val photoError: Boolean = false,
) {
    val avatarKind: AvatarKind
        get() =
            when {
                pendingPhotoPath != null -> AvatarKind.Photo
                existingPhotoStamp != null && emoji == null -> AvatarKind.Photo
                !emoji.isNullOrBlank() -> AvatarKind.Emoji
                else -> AvatarKind.Initial
            }
}

enum class AvatarKind {
    Initial,
    Emoji,
    Photo,
}

sealed interface NameError {
    data object Empty : NameError

    data object TooLong : NameError

    data object Invalid : NameError

    data object Duplicate : NameError
}

sealed interface ProfileFormEvent {
    data class NameChanged(
        val text: String,
    ) : ProfileFormEvent

    data class ColorPicked(
        val colorIndex: Int,
    ) : ProfileFormEvent

    data class EmojiPicked(
        val emoji: String?,
    ) : ProfileFormEvent

    data class PhotoCropped(
        val path: String,
    ) : ProfileFormEvent

    data object AvatarCleared : ProfileFormEvent

    data class CustomColorPicked(
        val argb: Int,
    ) : ProfileFormEvent

    data object Submit : ProfileFormEvent

    data object SaveFailed : ProfileFormEvent

    data object PhotoError : ProfileFormEvent

    data object ClearPhotoError : ProfileFormEvent
}

data class SubmitRequest(
    val name: String,
    val colorIndex: Int,
    val emoji: String?,
    val customColor: Int? = null,
    val pendingPhotoPath: String? = null,
    val existingPhotoStamp: Long? = null,
    val avatarKind: AvatarKind =
        when {
            pendingPhotoPath != null -> AvatarKind.Photo
            existingPhotoStamp != null && emoji == null -> AvatarKind.Photo
            !emoji.isNullOrBlank() -> AvatarKind.Emoji
            else -> AvatarKind.Initial
        },
)

fun truncateToCodePoints(
    text: String,
    maxCodePoints: Int,
): String {
    if (text.codePointCount(0, text.length) <= maxCodePoints) return text
    var cpCount = 0
    var offset = 0
    while (offset < text.length && cpCount < maxCodePoints) {
        val cp = text.codePointAt(offset)
        offset += Character.charCount(cp)
        cpCount++
    }
    return text.substring(0, offset)
}

fun defaultStateForSetup(owner: Profile): ProfileFormState =
    ProfileFormState(
        name = owner.name,
        colorIndex = owner.avatarColor,
        emoji = owner.avatarEmoji,
        customColor = owner.avatarCustomColor,
        pendingPhotoPath = null,
    )

fun defaultStateForAdd(existingProfiles: Collection<Profile>): ProfileFormState =
    ProfileFormState(
        name = "",
        colorIndex = nextFreeAvatarColor(existingProfiles.map { it.avatarColor }),
        emoji = null,
        customColor = null,
        pendingPhotoPath = null,
    )

fun defaultStateForEdit(profile: Profile): ProfileFormState =
    ProfileFormState(
        name = profile.name,
        colorIndex = profile.avatarColor,
        emoji = profile.avatarEmoji,
        customColor = profile.avatarCustomColor,
        pendingPhotoPath = null,
        existingPhotoStamp = profile.avatarPhotoStamp,
        existingPhotoProfileId = if (profile.avatarPhotoStamp != null) profile.id else null,
    )

fun otherNamesForEdit(
    profiles: Collection<Profile>,
    editingId: Long,
): List<String> = profiles.filter { it.id != editingId }.map { it.name }

fun reduceProfileForm(
    state: ProfileFormState,
    event: ProfileFormEvent,
    otherNames: Collection<String>,
): Pair<ProfileFormState, SubmitRequest?> =
    when (event) {
        is ProfileFormEvent.NameChanged -> {
            val truncated = truncateToCodePoints(event.text, PROFILE_NAME_MAX_LENGTH)
            Pair(state.copy(name = truncated, nameError = null, saveFailed = false), null)
        }

        is ProfileFormEvent.ColorPicked -> {
            Pair(
                state.copy(
                    colorIndex = normalizeAvatarColor(event.colorIndex),
                    customColor = null,
                ),
                null,
            )
        }

        is ProfileFormEvent.CustomColorPicked -> {
            Pair(
                state.copy(
                    customColor = forceOpaque(event.argb),
                ),
                null,
            )
        }

        is ProfileFormEvent.EmojiPicked -> {
            Pair(reduceAvatarEmoji(state, event.emoji), null)
        }

        is ProfileFormEvent.PhotoCropped -> {
            Pair(
                state.copy(
                    pendingPhotoPath = event.path,
                    emoji = null,
                    photoError = false,
                ),
                null,
            )
        }

        is ProfileFormEvent.AvatarCleared -> {
            Pair(
                state.copy(
                    emoji = null,
                    pendingPhotoPath = null,
                ),
                null,
            )
        }

        is ProfileFormEvent.PhotoError -> {
            Pair(state.copy(photoError = true), null)
        }

        is ProfileFormEvent.ClearPhotoError -> {
            Pair(state.copy(photoError = false), null)
        }

        is ProfileFormEvent.SaveFailed -> {
            Pair(state.copy(saving = false, saveFailed = true), null)
        }

        is ProfileFormEvent.Submit -> {
            reduceSubmit(state, otherNames)
        }
    }

private fun reduceSubmit(
    state: ProfileFormState,
    otherNames: Collection<String>,
): Pair<ProfileFormState, SubmitRequest?> {
    if (state.saving) return Pair(state, null)

    return when (val check = validateProfileName(state.name, otherNames)) {
        is NameCheck.Valid -> {
            Pair(
                state.copy(saving = true, nameError = null, saveFailed = false),
                SubmitRequest(
                    name = check.name,
                    colorIndex = state.colorIndex,
                    emoji = state.emoji,
                    customColor = state.customColor,
                    pendingPhotoPath = state.pendingPhotoPath,
                    existingPhotoStamp = state.existingPhotoStamp,
                    avatarKind = state.avatarKind,
                ),
            )
        }

        is NameCheck.Empty -> {
            Pair(state.copy(nameError = NameError.Empty), null)
        }

        is NameCheck.TooLong -> {
            Pair(state.copy(nameError = NameError.TooLong), null)
        }

        is NameCheck.Invalid -> {
            Pair(state.copy(nameError = NameError.Invalid), null)
        }

        is NameCheck.Duplicate -> {
            Pair(state.copy(nameError = NameError.Duplicate), null)
        }
    }
}

private fun reduceAvatarEmoji(
    state: ProfileFormState,
    emoji: String?,
): ProfileFormState =
    if (emoji != null && !isValidAvatarEmoji(emoji)) {
        state
    } else {
        state.copy(emoji = emoji, pendingPhotoPath = null)
    }
