package com.drift.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameRulesTest {
    @Test
    fun NR1_blank_isRejected() {
        assertEquals(NameResult.Blank, ProfileNameRules.validate("", emptyList()))
        assertEquals(NameResult.Blank, ProfileNameRules.validate("   ", emptyList()))
        assertEquals(NameResult.Blank, ProfileNameRules.validate("\t\n", emptyList()))
    }

    @Test
    fun NR2_moreThan20Characters_isRejected() {
        val twentyOneChars = "a".repeat(21)
        assertEquals(NameResult.TooLong, ProfileNameRules.validate(twentyOneChars, emptyList()))
        val trimmedTooLong = "   " + "b".repeat(21) + "   "
        assertEquals(NameResult.TooLong, ProfileNameRules.validate(trimmedTooLong, emptyList()))
    }

    @Test
    fun NR3_duplicateIgnoringCase_isRejected() {
        val existing = listOf("Alice", "Bob")
        assertEquals(NameResult.Duplicate, ProfileNameRules.validate("alice", existing))
        assertEquals(NameResult.Duplicate, ProfileNameRules.validate("ALICE", existing))
        assertEquals(NameResult.Duplicate, ProfileNameRules.validate("  bob  ", existing))
    }

    @Test
    fun NR4_validName_isTrimmed() {
        val result = ProfileNameRules.validate("  Alice Bob  ", listOf("Charlie"))
        assertTrue(result is NameResult.Valid)
        assertEquals("Alice Bob", (result as NameResult.Valid).trimmedName)

        val exactTwenty = "a".repeat(20)
        val resultTwenty = ProfileNameRules.validate(exactTwenty, emptyList())
        assertEquals(NameResult.Valid(exactTwenty), resultTwenty)
    }
}
