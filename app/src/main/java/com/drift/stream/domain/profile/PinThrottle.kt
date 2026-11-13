package com.drift.stream.domain.profile

import kotlin.math.min

private const val THIRTY_SECONDS_MS = 30_000L
private const val ONE_MINUTE_MS = 60_000L
private const val FIVE_MINUTES_MS = 300_000L
private const val FIFTEEN_MINUTES_MS = 900_000L
const val PIN_MAX_LOCK_MS = 3_600_000L // 60 min

private const val LOCK_TIER_ONE = 1
private const val LOCK_TIER_TWO = 2
private const val LOCK_TIER_THREE = 3
private const val LOCK_TIER_FOUR = 4

fun lockDurationMs(failedAttempts: Int): Long =
    if (failedAttempts <= 0 || failedAttempts % PIN_ATTEMPTS_PER_LOCK != 0) {
        0L
    } else {
        when (failedAttempts / PIN_ATTEMPTS_PER_LOCK) {
            LOCK_TIER_ONE -> THIRTY_SECONDS_MS
            LOCK_TIER_TWO -> ONE_MINUTE_MS
            LOCK_TIER_THREE -> FIVE_MINUTES_MS
            LOCK_TIER_FOUR -> FIFTEEN_MINUTES_MS
            else -> PIN_MAX_LOCK_MS
        }
    }

fun remainingLockMs(
    lockedUntil: Long?,
    now: Long,
    maxMs: Long = PIN_MAX_LOCK_MS,
): Long =
    if (lockedUntil == null || lockedUntil <= now) {
        0L
    } else {
        min(lockedUntil - now, maxMs)
    }

fun triesLeft(failedAttempts: Int): Int = PIN_ATTEMPTS_PER_LOCK - (failedAttempts % PIN_ATTEMPTS_PER_LOCK)
