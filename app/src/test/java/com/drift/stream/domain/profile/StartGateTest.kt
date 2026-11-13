package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGateTest {
    private fun p(
        id: Long,
        owner: Boolean = false,
        needsSetup: Boolean = false,
        locked: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = "Profile $id",
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = owner,
            needsSetup = needsSetup,
            isLocked = locked,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
        )

    @Test
    fun G1_emptyProfileList_resolving() {
        val gate =
            decideStartGate(
                profiles = emptyList(),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Resolving, gate)
    }

    @Test
    fun G2_activeUnresolved_resolving() {
        val owner = p(1L, owner = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = null,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Resolving, gate)
    }

    @Test
    fun G3_ownerNeedsSetup_setupForOwner() {
        val owner = p(1L, owner = true, needsSetup = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Setup(1L), gate)
    }

    @Test
    fun G4_ownerNeedsSetup_beatsPickerRules() {
        val owner = p(1L, owner = true, needsSetup = true)
        val p2 = p(2L)
        val gate =
            decideStartGate(
                profiles = listOf(owner, p2),
                activeProfileId = 1L,
                alwaysShowPicker = true,
                choseThisSession = false,
            )
        assertEquals(StartGate.Setup(1L), gate)
    }

    @Test
    fun G5_oneUnlockedProfile_enter() {
        val owner = p(1L, owner = true, needsSetup = false)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Enter, gate)
    }

    @Test
    fun G6_twoProfiles_notChosen_picker() {
        val owner = p(1L, owner = true)
        val p2 = p(2L)
        val gate =
            decideStartGate(
                profiles = listOf(owner, p2),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Picker, gate)
    }

    @Test
    fun G7_twoProfiles_chosen_enter() {
        val owner = p(1L, owner = true)
        val p2 = p(2L)
        val gate =
            decideStartGate(
                profiles = listOf(owner, p2),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = true,
            )
        assertEquals(StartGate.Enter, gate)
    }

    @Test
    fun G8_oneLockedProfile_notChosen_picker() {
        val owner = p(1L, owner = true, locked = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Picker, gate)
    }

    @Test
    fun G9_alwaysShowPicker_oneProfile_picker() {
        val owner = p(1L, owner = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = 1L,
                alwaysShowPicker = true,
                choseThisSession = false,
            )
        assertEquals(StartGate.Picker, gate)
    }

    @Test
    fun G10_alwaysShowPicker_chosen_enter() {
        val owner = p(1L, owner = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = 1L,
                alwaysShowPicker = true,
                choseThisSession = true,
            )
        assertEquals(StartGate.Enter, gate)
    }

    @Test
    fun G11_activeNotInList_resolving() {
        val owner = p(1L, owner = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner),
                activeProfileId = 7L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Resolving, gate)
    }

    @Test
    fun G12_noOwnerInList_resolving() {
        val p2 = p(2L, owner = false)
        val gate =
            decideStartGate(
                profiles = listOf(p2),
                activeProfileId = 2L,
                alwaysShowPicker = false,
                choseThisSession = false,
            )
        assertEquals(StartGate.Resolving, gate)
    }

    @Test
    fun G13_nonOwnerNeedsSetup_ignored() {
        val owner = p(1L, owner = true, needsSetup = false)
        val p2 = p(2L, owner = false, needsSetup = true)
        val gate =
            decideStartGate(
                profiles = listOf(owner, p2),
                activeProfileId = 1L,
                alwaysShowPicker = false,
                choseThisSession = true,
            )
        assertEquals(StartGate.Enter, gate)
    }

    @Test
    fun H1_resolving_holds_whileUnderMax() {
        assertTrue(shouldHoldFirstFrame(StartGate.Resolving, themeReady = true, elapsedMs = 100L))
    }

    @Test
    fun H2_resolving_releasesAtMax() {
        assertFalse(shouldHoldFirstFrame(StartGate.Resolving, themeReady = true, elapsedMs = 1500L))
    }

    @Test
    fun H3_themeNotReady_holds() {
        assertTrue(shouldHoldFirstFrame(StartGate.Enter, themeReady = false, elapsedMs = 100L))
    }

    @Test
    fun H4_resolvedAndThemeReady_releases() {
        assertFalse(shouldHoldFirstFrame(StartGate.Picker, themeReady = true, elapsedMs = 0L))
    }
}
