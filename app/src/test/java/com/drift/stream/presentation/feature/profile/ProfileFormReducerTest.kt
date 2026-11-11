package com.drift.stream.presentation.feature.profile

import com.drift.stream.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormReducerTest {
    private fun sampleOwner(
        name: String = "Me",
        avatarColor: Int = 4,
        avatarEmoji: String? = "🦊",
    ): Profile =
        Profile(
            id = 1L,
            name = name,
            avatarColor = avatarColor,
            avatarEmoji = avatarEmoji,
            isOwner = true,
            needsSetup = true,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
        )

    @Test
    fun F1_nameChanged_updatesNameAndClearsError() {
        val initial =
            ProfileFormState(
                name = "",
                colorIndex = 0,
                emoji = null,
                nameError = NameError.Empty,
                saveFailed = true,
            )
        val (state, request) = reduceProfileForm(initial, ProfileFormEvent.NameChanged("A"), emptyList())
        assertEquals("A", state.name)
        assertNull(state.nameError)
        assertFalse(state.saveFailed)
        assertNull(request)
    }

    @Test
    fun F2_nameChanged_truncatesToMaxCodePoints() {
        val thirtyAs = "a".repeat(30)
        val (state1, _) =
            reduceProfileForm(
                ProfileFormState("a", 0, null),
                ProfileFormEvent.NameChanged(thirtyAs),
                emptyList(),
            )
        assertEquals(20, state1.name.length)

        val nineteenAsAndTwoEmojis = "a".repeat(19) + "🎬🎬"
        val (state2, _) =
            reduceProfileForm(
                ProfileFormState("a", 0, null),
                ProfileFormEvent.NameChanged(nineteenAsAndTwoEmojis),
                emptyList(),
            )
        assertEquals(20, state2.name.codePointCount(0, state2.name.length))
        assertEquals("a".repeat(19) + "🎬", state2.name)
    }

    @Test
    fun F3_colorPicked_updatesIndex() {
        val initial = ProfileFormState("Asha", 0, null)
        val (state1, _) = reduceProfileForm(initial, ProfileFormEvent.ColorPicked(5), emptyList())
        assertEquals(5, state1.colorIndex)

        val (state2, _) = reduceProfileForm(initial, ProfileFormEvent.ColorPicked(14), emptyList())
        assertEquals(2, state2.colorIndex)
    }

    @Test
    fun F4_emojiPicked_setsAndClears() {
        val initial = ProfileFormState("Asha", 0, null)
        val (state1, _) = reduceProfileForm(initial, ProfileFormEvent.EmojiPicked("🦊"), emptyList())
        assertEquals("🦊", state1.emoji)

        val (state2, _) = reduceProfileForm(state1, ProfileFormEvent.EmojiPicked(null), emptyList())
        assertNull(state2.emoji)
    }

    @Test
    fun F5_submit_invalidName_setsErrorAndNoRequest() {
        val emptyState = ProfileFormState("", 0, null)
        val (resEmpty, reqEmpty) = reduceProfileForm(emptyState, ProfileFormEvent.Submit, emptyList())
        assertEquals(NameError.Empty, resEmpty.nameError)
        assertFalse(resEmpty.saving)
        assertNull(reqEmpty)

        val longState = ProfileFormState("a".repeat(21), 0, null)
        val (resLong, reqLong) = reduceProfileForm(longState, ProfileFormEvent.Submit, emptyList())
        assertEquals(NameError.TooLong, resLong.nameError)
        assertFalse(resLong.saving)
        assertNull(reqLong)

        val invalidState = ProfileFormState("A\nB", 0, null)
        val (resInvalid, reqInvalid) = reduceProfileForm(invalidState, ProfileFormEvent.Submit, emptyList())
        assertEquals(NameError.Invalid, resInvalid.nameError)
        assertFalse(resInvalid.saving)
        assertNull(reqInvalid)

        val duplicateState = ProfileFormState("Asha", 0, null)
        val (resDup, reqDup) =
            reduceProfileForm(duplicateState, ProfileFormEvent.Submit, listOf("Asha"))
        assertEquals(NameError.Duplicate, resDup.nameError)
        assertFalse(resDup.saving)
        assertNull(reqDup)
    }

    @Test
    fun F6_submit_valid_setsSavingAndReturnsRequest() {
        val state = ProfileFormState("  Asha ", 3, "🚀")
        val (newState, request) = reduceProfileForm(state, ProfileFormEvent.Submit, emptyList())
        assertTrue(newState.saving)
        assertNull(newState.nameError)
        assertFalse(newState.saveFailed)
        assertEquals(SubmitRequest("Asha", 3, "🚀"), request)
    }

    @Test
    fun F7_submit_whileSaving_isIgnored() {
        val state = ProfileFormState("Asha", 0, null, saving = true)
        val (newState, request) = reduceProfileForm(state, ProfileFormEvent.Submit, emptyList())
        assertEquals(state, newState)
        assertNull(request)
    }

    @Test
    fun F8_saveFailed_clearsSavingKeepsInput() {
        val state = ProfileFormState("Asha", 2, "🦊", saving = true)
        val (newState, request) = reduceProfileForm(state, ProfileFormEvent.SaveFailed, emptyList())
        assertFalse(newState.saving)
        assertTrue(newState.saveFailed)
        assertEquals("Asha", newState.name)
        assertEquals(2, newState.colorIndex)
        assertEquals("🦊", newState.emoji)
        assertNull(request)
    }

    @Test
    fun F9_defaultStateForSetup_prefillsOwnerFields() {
        val owner = sampleOwner("Me", 4, "🦊")
        val state = defaultStateForSetup(owner)
        assertEquals("Me", state.name)
        assertEquals(4, state.colorIndex)
        assertEquals("🦊", state.emoji)

        // Submitting with owner's own name excluded is valid
        val otherNames = emptyList<String>()
        val (_, request) = reduceProfileForm(state, ProfileFormEvent.Submit, otherNames)
        assertEquals(SubmitRequest("Me", 4, "🦊"), request)
    }

    @Test
    fun F10_defaultStateForAdd_picksNextFreeColourAndEmptyName() {
        val profiles =
            listOf(
                sampleOwner(name = "One", avatarColor = 0),
                sampleOwner(name = "Two", avatarColor = 1),
            )
        val state = defaultStateForAdd(profiles)
        assertEquals("", state.name)
        assertEquals(2, state.colorIndex)
        assertNull(state.emoji)
    }
}
