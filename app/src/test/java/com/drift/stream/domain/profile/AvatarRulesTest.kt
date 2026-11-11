package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarRulesTest {
    @Test
    fun RC1_contentColor_onWhite_isBlack() {
        assertEquals(0xFF000000.toInt(), contentColorFor(0xFFFFFFFF.toInt()))
    }

    @Test
    fun RC2_contentColor_onBlack_isWhite() {
        assertEquals(0xFFFFFFFF.toInt(), contentColorFor(0xFF000000.toInt()))
    }

    @Test
    fun RC3_contentColor_onPaletteRed_isWhite() {
        assertEquals(0xFFFFFFFF.toInt(), contentColorFor(0xFFC62828.toInt()))
    }

    @Test
    fun RC4_contentColor_onYellow_isBlack() {
        assertEquals(0xFF000000.toInt(), contentColorFor(0xFFFFEB3B.toInt()))
    }

    @Test
    fun RC5_contentColor_onMidGrey_isBlack() {
        assertEquals(0xFF000000.toInt(), contentColorFor(0xFF777777.toInt()))
    }

    @Test
    fun RC6_forceOpaque_setsAlpha() {
        assertEquals(0xFF123456.toInt(), forceOpaque(0x00123456))
    }

    @Test
    fun E1_validEmoji_simple() {
        assertTrue(isValidAvatarEmoji("🦊"))
    }

    @Test
    fun E2_validEmoji_skinToneZwjSequence() {
        assertTrue(isValidAvatarEmoji("👩🏽‍💻"))
    }

    @Test
    fun E3_invalidEmoji_blank() {
        assertFalse(isValidAvatarEmoji(""))
        assertFalse(isValidAvatarEmoji("   "))
    }

    @Test
    fun E4_invalidEmoji_tooLong() {
        val thirtyThree = "a".repeat(33)
        val thirtyTwo = "a".repeat(32)
        assertFalse(isValidAvatarEmoji(thirtyThree))
        assertTrue(isValidAvatarEmoji(thirtyTwo))
    }

    @Test
    fun E5_invalidEmoji_controlCharacter() {
        assertFalse(isValidAvatarEmoji("🦊\n"))
    }
}
