package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile
import com.drift.stream.presentation.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRolesTest {
    private fun P(
        id: Long,
        isOwner: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = if (isOwner) "Owner" else "User$id",
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    @Test
    fun RL1_canManageProfiles_owner_true() {
        val active = P(id = 1L, isOwner = true)
        assertTrue(canManageProfiles(active))
    }

    @Test
    fun RL2_canManageProfiles_nonOwner_false() {
        val active = P(id = 2L, isOwner = false)
        assertFalse(canManageProfiles(active))
    }

    @Test
    fun RL3_canManageProfiles_noActive_false() {
        assertFalse(canManageProfiles(null))
    }

    @Test
    fun RL4_canEditProfile_owner_anyTarget() {
        val activeOwner = P(id = 1L, isOwner = true)
        val targetSelf = P(id = 1L, isOwner = true)
        val targetOther = P(id = 2L, isOwner = false)

        assertTrue(canEditProfile(target = targetSelf, active = activeOwner))
        assertTrue(canEditProfile(target = targetOther, active = activeOwner))
    }

    @Test
    fun RL5_canEditProfile_nonOwner_onlyOwnProfile() {
        val activeNonOwner = P(id = 2L, isOwner = false)
        val targetSelf = P(id = 2L, isOwner = false)
        val targetOwner = P(id = 1L, isOwner = true)
        val targetOther = P(id = 3L, isOwner = false)

        assertTrue(canEditProfile(target = targetSelf, active = activeNonOwner))
        assertFalse(canEditProfile(target = targetOwner, active = activeNonOwner))
        assertFalse(canEditProfile(target = targetOther, active = activeNonOwner))
    }

    @Test
    fun RL6_canEditProfile_noActive_false() {
        val target = P(id = 1L, isOwner = true)
        assertFalse(canEditProfile(target = target, active = null))
    }

    @Test
    fun ST1_settingsEntry_owner_manageList() {
        val owner = P(id = 1L, isOwner = true)
        assertEquals(SettingsProfileEntry.ManageList, settingsProfileEntryTarget(owner))
    }

    @Test
    fun ST2_settingsEntry_nonOwner_editSelf() {
        val nonOwner = P(id = 2L, isOwner = false)
        assertEquals(SettingsProfileEntry.EditSelf, settingsProfileEntryTarget(nonOwner))
    }

    @Test
    fun ST3_settingsEntry_noActive_none() {
        assertEquals(SettingsProfileEntry.None, settingsProfileEntryTarget(null))
    }

    @Test
    fun NV1_tabsToClearOnSwitch_isEveryTabExceptHome() {
        val expected = TopLevelDestination.entries.filter { it != TopLevelDestination.HOME }
        val actual = tabsToClearOnSwitch()
        assertEquals(expected, actual)
    }
}
