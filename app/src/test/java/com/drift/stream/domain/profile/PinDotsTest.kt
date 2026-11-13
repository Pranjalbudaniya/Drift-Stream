package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class PinDotsTest {
    @Test
    fun DS1_pinDotSlots_empty_isFour() {
        assertEquals(4, pinDotSlots(0))
    }

    @Test
    fun DS2_pinDotSlots_fourTyped_isFour() {
        assertEquals(4, pinDotSlots(4))
    }

    @Test
    fun DS3_pinDotSlots_fiveTyped_isFive() {
        assertEquals(5, pinDotSlots(5))
    }

    @Test
    fun DS4_pinDotSlots_sixTyped_isSix() {
        assertEquals(6, pinDotSlots(6))
    }

    @Test
    fun DS5_pinDotSlots_beyondMax_isSix() {
        assertEquals(6, pinDotSlots(9))
    }
}
