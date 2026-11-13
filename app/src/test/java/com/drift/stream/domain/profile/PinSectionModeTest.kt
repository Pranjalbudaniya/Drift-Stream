package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class PinSectionModeTest {
    private fun p(
        id: Long,
        owner: Boolean,
        locked: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = "Profile$id",
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = owner,
            needsSetup = false,
            createdAt = 0L,
            lastUsedAt = 0L,
            sortOrder = 0,
            isLocked = locked,
        )

    @Test
    fun PS1_ownerTarget_isFull_inAnyHost() {
        val owner = p(id = 1L, owner = true)
        val other = p(id = 2L, owner = false)
        assertEquals(PinSectionMode.Full, pinSectionMode(target = owner, active = other, host = DeleteHost.Picker))
        assertEquals(PinSectionMode.Full, pinSectionMode(target = owner, active = null, host = DeleteHost.Picker))
        assertEquals(PinSectionMode.Full, pinSectionMode(target = owner, active = other, host = DeleteHost.Settings))
        assertEquals(PinSectionMode.Full, pinSectionMode(target = owner, active = null, host = DeleteHost.Settings))
    }

    @Test
    fun PS2_pickerHost_nonOwnerTarget_isOwnerManage() {
        val nonOwner = p(id = 2L, owner = false)
        assertEquals(
            PinSectionMode.OwnerManage,
            pinSectionMode(target = nonOwner, active = null, host = DeleteHost.Picker),
        )
    }

    @Test
    fun PS3_settingsHost_ownProfile_isFull() {
        val profile2 = p(id = 2L, owner = false)
        assertEquals(
            PinSectionMode.Full,
            pinSectionMode(target = profile2, active = profile2, host = DeleteHost.Settings),
        )
    }

    @Test
    fun PS4_settingsHost_ownerActive_otherProfile_isOwnerManage() {
        val owner = p(id = 1L, owner = true)
        val profile2 = p(id = 2L, owner = false)
        assertEquals(
            PinSectionMode.OwnerManage,
            pinSectionMode(target = profile2, active = owner, host = DeleteHost.Settings),
        )
    }

    @Test
    fun PS5_settingsHost_nonOwnerActive_otherProfile_isNone() {
        val profile2 = p(id = 2L, owner = false)
        val profile3 = p(id = 3L, owner = false)
        assertEquals(
            PinSectionMode.None,
            pinSectionMode(target = profile3, active = profile2, host = DeleteHost.Settings),
        )
    }

    @Test
    fun PS6_settingsHost_noActive_isNone() {
        val nonOwner = p(id = 2L, owner = false)
        assertEquals(PinSectionMode.None, pinSectionMode(target = nonOwner, active = null, host = DeleteHost.Settings))
    }
}
