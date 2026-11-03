package com.drift.stream.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLifecycleRulesTest {
    @Test
    fun SL1_idleShorterThanGrace_doesNotStop() {
        // no activity for 4 999 ms / shouldStopService / false
        val idleSince = 10_000L
        val now = 14_999L
        val stop = shouldStopService(hasActive = false, idleSinceMs = idleSince, nowMs = now)
        assertFalse(stop)
    }

    @Test
    fun SL2_idleForTheGrace_stops() {
        // no activity for 5 000 ms / shouldStopService / true
        val idleSince = 10_000L
        val now = 15_000L
        val stop = shouldStopService(hasActive = false, idleSinceMs = idleSince, nowMs = now)
        assertTrue(stop)
    }

    @Test
    fun SL3_active_neverStops() {
        // hasActive true, idleSince long ago / shouldStopService / false
        val idleSince = 10_000L
        val now = 50_000L
        val stop = shouldStopService(hasActive = true, idleSinceMs = idleSince, nowMs = now)
        assertFalse(stop)
    }

    @Test
    fun SL4_start_isIgnoredWhileTheServiceRuns() {
        // serviceRunning true / shouldStartService / false
        val start = shouldStartService(serviceRunning = true, lastStartAtMs = null, nowMs = 10_000L)
        assertFalse(start)
    }

    @Test
    fun SL5_start_isRateLimitedToOneSecond() {
        // not running, last start 400 ms ago / shouldStartService / false
        val lastStart = 10_000L
        val now = 10_400L
        val start = shouldStartService(serviceRunning = false, lastStartAtMs = lastStart, nowMs = now)
        assertFalse(start)
    }

    @Test
    fun SL6_start_isAllowedAfterTheGap() {
        // not running, last start 1 000 ms ago, and never started / shouldStartService / true, true
        val startAfterGap = shouldStartService(serviceRunning = false, lastStartAtMs = 10_000L, nowMs = 11_000L)
        val startNever = shouldStartService(serviceRunning = false, lastStartAtMs = null, nowMs = 11_000L)
        assertTrue(startAfterGap)
        assertTrue(startNever)
    }
}
