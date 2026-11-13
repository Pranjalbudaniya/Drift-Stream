package com.drift.stream.domain.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {
    @Test
    fun PN1_validPin_fourDigits() {
        assertTrue(isValidPin("1234"))
    }

    @Test
    fun PN2_validPin_sixDigits() {
        assertTrue(isValidPin("123456"))
    }

    @Test
    fun PN3_invalidPin_threeDigits() {
        assertFalse(isValidPin("123"))
    }

    @Test
    fun PN4_invalidPin_sevenDigits() {
        assertFalse(isValidPin("1234567"))
    }

    @Test
    fun PN5_invalidPin_letterOrSpace() {
        assertFalse(isValidPin("12a4"))
        assertFalse(isValidPin("12 4"))
    }

    @Test
    fun PN6_invalidPin_unicodeDigits() {
        assertFalse(isValidPin("١٢٣٤"))
    }

    @Test
    fun PN7_invalidPin_empty() {
        assertFalse(isValidPin(""))
    }
}
