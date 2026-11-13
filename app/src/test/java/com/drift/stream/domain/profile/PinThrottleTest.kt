package com.drift.stream.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class PinThrottleTest {
    @Test
    fun TH1_noLock_belowFiveFailures() {
        for (failures in 0..4) {
            assertEquals(0L, lockDurationMs(failures))
        }
    }

    @Test
    fun TH2_fifthFailure_locks30s() {
        assertEquals(30_000L, lockDurationMs(5))
    }

    @Test
    fun TH3_tenthFailure_locks60s() {
        assertEquals(60_000L, lockDurationMs(10))
    }

    @Test
    fun TH4_fifteenthFailure_locks5min() {
        assertEquals(300_000L, lockDurationMs(15))
    }

    @Test
    fun TH5_twentiethFailure_locks15min() {
        assertEquals(900_000L, lockDurationMs(20))
    }

    @Test
    fun TH6_twentyFiveAndMore_lock60min() {
        assertEquals(3_600_000L, lockDurationMs(25))
        assertEquals(3_600_000L, lockDurationMs(100))
    }

    @Test
    fun TH7_nonMultiples_noLock() {
        listOf(6, 7, 9, 11).forEach { failures ->
            assertEquals(0L, lockDurationMs(failures))
        }
    }

    @Test
    fun TH8_remainingLock_pastNullFutureAndClockTravel() {
        val now = 1_000_000L
        assertEquals(0L, remainingLockMs(null, now))
        assertEquals(0L, remainingLockMs(now - 1000L, now))
        assertEquals(10_000L, remainingLockMs(now + 10_000L, now))
        val oneYearLater = now + 365L * 24 * 3600 * 1000
        assertEquals(PIN_MAX_LOCK_MS, remainingLockMs(oneYearLater, now))
    }
}
