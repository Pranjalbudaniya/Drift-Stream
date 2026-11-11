package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile

const val FIRST_FRAME_MAX_HOLD_MS = 1500L
const val CHOOSE_TIMEOUT_MS = 3000L

sealed interface StartGate {
    data object Resolving : StartGate

    data class Setup(
        val profileId: Long,
    ) : StartGate

    data object Picker : StartGate

    data object Enter : StartGate
}

fun decideStartGate(
    profiles: List<Profile>,
    activeProfileId: Long?,
    alwaysShowPicker: Boolean,
    choseThisSession: Boolean,
): StartGate {
    val owner = profiles.firstOrNull { it.isOwner }
    return when {
        owner == null || isResolving(profiles, activeProfileId) -> StartGate.Resolving
        owner.needsSetup -> StartGate.Setup(owner.id)
        !choseThisSession && shouldShowPicker(profiles, alwaysShowPicker) -> StartGate.Picker
        else -> StartGate.Enter
    }
}

private fun isResolving(
    profiles: List<Profile>,
    activeProfileId: Long?,
): Boolean {
    if (profiles.isEmpty() || activeProfileId == null) return true
    return profiles.none { it.id == activeProfileId }
}

private fun shouldShowPicker(
    profiles: List<Profile>,
    alwaysShowPicker: Boolean,
): Boolean = profiles.size >= 2 || profiles.any { it.isLocked } || alwaysShowPicker

fun shouldHoldFirstFrame(
    gate: StartGate,
    themeReady: Boolean,
    elapsedMs: Long,
): Boolean = (gate is StartGate.Resolving || !themeReady) && elapsedMs < FIRST_FRAME_MAX_HOLD_MS
