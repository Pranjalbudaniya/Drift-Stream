package com.drift.stream.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTraceTest {
    @Test
    fun PT1_disabled_writesNothing() {
        var callCount = 0
        val trace =
            PlayerTrace(
                enabled = { false },
                clock = { 1000L },
                sink = { callCount++ },
            )

        trace.log("vm.toggle", "playing" to true)

        assertEquals(0, callCount)
    }

    @Test
    fun PT2_enabled_writesOneLineWithEventAndFieldsInOrder() {
        val lines = mutableListOf<String>()
        val trace =
            PlayerTrace(
                enabled = { true },
                clock = { 1234L },
                sink = { lines.add(it) },
            )

        trace.log("vm.toggle", "playing" to false, "focus" to true)

        assertEquals(listOf("t=1234 vm.toggle playing=false focus=true"), lines)
    }

    @Test
    fun PT3_valuesThatLookLikeUrls_areRedacted() {
        val lines = mutableListOf<String>()
        val trace =
            PlayerTrace(
                enabled = { true },
                clock = { 1000L },
                sink = { lines.add(it) },
            )

        trace.log("x", "u" to "http://host/path", "n" to 5)

        assertEquals(1, lines.size)
        val line = lines[0]
        assertTrue(line.contains("u=<url>"))
        assertTrue(line.contains("n=5"))
        assertFalse(line.contains("host"))
    }
}
