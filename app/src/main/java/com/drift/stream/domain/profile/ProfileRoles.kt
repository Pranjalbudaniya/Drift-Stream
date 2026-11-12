package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile
import com.drift.stream.presentation.navigation.TopLevelDestination

fun canManageProfiles(active: Profile?): Boolean = active?.isOwner == true

fun canEditProfile(
    target: Profile,
    active: Profile?,
): Boolean = active != null && (active.isOwner || active.id == target.id)

enum class SettingsProfileEntry {
    ManageList,
    EditSelf,
    None,
}

fun settingsProfileEntryTarget(active: Profile?): SettingsProfileEntry =
    when {
        active == null -> SettingsProfileEntry.None
        active.isOwner -> SettingsProfileEntry.ManageList
        else -> SettingsProfileEntry.EditSelf
    }

fun tabsToClearOnSwitch(): List<TopLevelDestination> =
    TopLevelDestination.entries.filter { it != TopLevelDestination.HOME }
