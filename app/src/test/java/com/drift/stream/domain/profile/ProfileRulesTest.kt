package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test
    fun N1_validName_returnsTrimmed() {
        val result = validateProfileName("  Asha  ", emptyList())
        assertEquals(NameCheck.Valid("Asha"), result)
    }

    @Test
    fun N2_empty_returnsEmpty() {
        val result = validateProfileName("", emptyList())
        assertEquals(NameCheck.Empty, result)
    }

    @Test
    fun N3_blankAndNbsp_returnsEmpty() {
        assertEquals(NameCheck.Empty, validateProfileName("   ", emptyList()))
        assertEquals(NameCheck.Empty, validateProfileName("\u00A0\u00A0", emptyList()))
    }

    @Test
    fun N4_twentyCodePoints_isValid() {
        val name = "a".repeat(20)
        assertEquals(NameCheck.Valid(name), validateProfileName(name, emptyList()))
    }

    @Test
    fun N5_twentyOneCodePoints_returnsTooLong() {
        val name = "a".repeat(21)
        assertEquals(NameCheck.TooLong, validateProfileName(name, emptyList()))
    }

    @Test
    fun N6_emojiCountsAsOneCodePoint() {
        val twentyEmojis = "🎬".repeat(20)
        assertEquals(NameCheck.Valid(twentyEmojis), validateProfileName(twentyEmojis, emptyList()))

        val twentyOneEmojis = "🎬".repeat(21)
        assertEquals(NameCheck.TooLong, validateProfileName(twentyOneEmojis, emptyList()))
    }

    @Test
    fun N7_controlCharacter_returnsInvalid() {
        val result = validateProfileName("A\nB", emptyList())
        assertEquals(NameCheck.Invalid, result)
    }

    @Test
    fun N8_duplicateIgnoringCase_returnsDuplicate() {
        val result = validateProfileName("asha", listOf("Asha"))
        assertEquals(NameCheck.Duplicate, result)
    }

    @Test
    fun N9_duplicateIgnoresTrimmedSpaces() {
        val result = validateProfileName(" Asha ", listOf("Asha "))
        assertEquals(NameCheck.Duplicate, result)
    }

    @Test
    fun I1_firstLetterUppercased() {
        assertEquals("A", avatarInitial("asha"))
    }

    @Test
    fun I2_leadingSpacesAndSymbolsSkipped() {
        assertEquals("B", avatarInitial("  -bob"))
    }

    @Test
    fun I3_digitAllowed() {
        assertEquals("7", avatarInitial("7up"))
    }

    @Test
    fun I4_noLetterOrDigit_returnsQuestionMark() {
        assertEquals("?", avatarInitial("😎"))
        assertEquals("?", avatarInitial("..."))
        assertEquals("?", avatarInitial(""))
    }

    @Test
    fun I5_surrogatePairLetter() {
        // Mathematical script capital A (U+1D49C: 𝒜)
        val initial = avatarInitial("𝒜x")
        assertEquals("𝒜", initial)
    }

    @Test
    fun I6_nonAsciiUppercase() {
        assertEquals("É", avatarInitial("émile"))
    }

    @Test
    fun I7_sharpS_staysSingleCharacter() {
        val initial = avatarInitial("ß")
        assertTrue(initial.length == 1 || initial.codePointCount(0, initial.length) == 1)
        assertTrue(initial != "SS")
    }

    @Test
    fun C1_firstUnusedIndex() {
        assertEquals(2, nextFreeAvatarColor(listOf(0, 1, 3)))
    }

    @Test
    fun C2_allUsed_picksLeastUsedThenLowest() {
        val used = (0..11).toList() + listOf(0)
        assertEquals(1, nextFreeAvatarColor(used))
    }

    @Test
    fun C3_outOfRangeUsedValuesAreNormalized() {
        assertEquals(1, nextFreeAvatarColor(listOf(12, -1)))
    }
}
