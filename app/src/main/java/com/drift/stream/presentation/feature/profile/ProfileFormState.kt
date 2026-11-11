package com.drift.stream.presentation.feature.profile

import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.NameCheck
import com.drift.stream.domain.profile.PROFILE_NAME_MAX_LENGTH
import com.drift.stream.domain.profile.nextFreeAvatarColor
import com.drift.stream.domain.profile.normalizeAvatarColor
import com.drift.stream.domain.profile.validateProfileName

data class ProfileFormState(
    val name: String,
    val colorIndex: Int,
    val emoji: String?,
    val nameError: NameError? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

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

    data object Submit : ProfileFormEvent

    data object SaveFailed : ProfileFormEvent
}

data class SubmitRequest(
    val name: String,
    val colorIndex: Int,
    val emoji: String?,
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
    )

fun defaultStateForAdd(existingProfiles: Collection<Profile>): ProfileFormState =
    ProfileFormState(
        name = "",
        colorIndex = nextFreeAvatarColor(existingProfiles.map { it.avatarColor }),
        emoji = null,
    )

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
            Pair(state.copy(colorIndex = normalizeAvatarColor(event.colorIndex)), null)
        }

        is ProfileFormEvent.EmojiPicked -> {
            Pair(state.copy(emoji = event.emoji), null)
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
                SubmitRequest(check.name, state.colorIndex, state.emoji),
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
