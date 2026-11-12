package com.drift.stream.presentation.feature.profile

import com.drift.stream.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditProfileStateTest {
    private fun sampleProfile(
        id: Long,
        name: String,
        avatarColor: Int = 0,
        avatarEmoji: String? = null,
        avatarPhotoStamp: Long? = null,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = avatarColor,
            avatarEmoji = avatarEmoji,
            isOwner = false,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
            avatarCustomColor = null,
            avatarPhotoStamp = avatarPhotoStamp,
        )

    @Test
    fun ED1_defaultStateForEdit_prefillsAllFields() {
        val profile =
            sampleProfile(
                id = 1L,
                name = "Asha",
                avatarColor = 4,
            )
        val state = defaultStateForEdit(profile)
        assertEquals("Asha", state.name)
        assertEquals(4, state.colorIndex)
        assertNull(state.emoji)
        assertNull(state.customColor)
        assertNull(state.existingPhotoStamp)
        assertEquals(AvatarKind.Initial, state.avatarKind)
    }

    @Test
    fun ED2_defaultStateForEdit_withEmojiAndCustomColour() {
        val custom = 0xFFFF8800.toInt()
        val profile =
            sampleProfile(
                id = 1L,
                name = "Asha",
                avatarColor = 4,
                avatarEmoji = "🦊",
            ).copy(avatarCustomColor = custom)
        val state = defaultStateForEdit(profile)
        assertEquals("🦊", state.emoji)
        assertEquals(custom, state.customColor)
        assertEquals(AvatarKind.Emoji, state.avatarKind)
    }

    @Test
    fun ED3_defaultStateForEdit_withPhoto_marksExistingPhoto() {
        val profile =
            sampleProfile(
                id = 1L,
                name = "Asha",
                avatarColor = 4,
                avatarPhotoStamp = 123L,
            )
        val state = defaultStateForEdit(profile)
        assertEquals(AvatarKind.Photo, state.avatarKind)
        assertEquals(123L, state.existingPhotoStamp)
    }

    @Test
    fun ED4_otherNamesForEdit_excludesOnlyTheEditedProfile() {
        val profiles =
            listOf(
                sampleProfile(id = 1L, name = "Asha"),
                sampleProfile(id = 2L, name = "Dev"),
            )
        val other = otherNamesForEdit(profiles, editingId = 1L)
        assertEquals(listOf("Dev"), other)
    }
}
