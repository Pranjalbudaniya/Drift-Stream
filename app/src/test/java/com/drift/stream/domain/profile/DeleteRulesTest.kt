package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteRulesTest {
    private fun sampleProfile(
        id: Long,
        isOwner: Boolean,
    ): Profile =
        Profile(
            id = id,
            name = "Test",
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
        )

    @Test
    fun DL1_owner_neverDeletable() {
        val owner = sampleProfile(id = 1L, isOwner = true)
        assertFalse(canDeleteProfile(owner, activeProfileId = 1L, host = DeleteHost.Picker))
        assertFalse(canDeleteProfile(owner, activeProfileId = 1L, host = DeleteHost.Settings))
        assertFalse(canDeleteProfile(owner, activeProfileId = 2L, host = DeleteHost.Picker))
        assertFalse(canDeleteProfile(owner, activeProfileId = 2L, host = DeleteHost.Settings))
        assertFalse(canDeleteProfile(owner, activeProfileId = null, host = DeleteHost.Picker))
        assertFalse(canDeleteProfile(owner, activeProfileId = null, host = DeleteHost.Settings))
    }

    @Test
    fun DL2_activeProfile_notDeletableFromSettings() {
        val profile = sampleProfile(id = 2L, isOwner = false)
        assertFalse(canDeleteProfile(profile, activeProfileId = 2L, host = DeleteHost.Settings))
    }

    @Test
    fun DL3_activeProfile_deletableFromPicker() {
        val profile = sampleProfile(id = 2L, isOwner = false)
        assertTrue(canDeleteProfile(profile, activeProfileId = 2L, host = DeleteHost.Picker))
    }

    @Test
    fun DL4_nonOwnerNonActive_deletableFromBothHosts() {
        val profile = sampleProfile(id = 2L, isOwner = false)
        assertTrue(canDeleteProfile(profile, activeProfileId = 3L, host = DeleteHost.Picker))
        assertTrue(canDeleteProfile(profile, activeProfileId = 3L, host = DeleteHost.Settings))
    }
}
