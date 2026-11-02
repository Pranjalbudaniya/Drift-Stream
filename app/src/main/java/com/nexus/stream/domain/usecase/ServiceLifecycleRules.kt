package com.nexus.stream.domain.usecase

const val SERVICE_GRACE_MS = 5_000L
const val SERVICE_MIN_GAP_MS = 1_000L

fun shouldStopService(
    hasActive: Boolean,
    idleSinceMs: Long?,
    nowMs: Long,
    graceMs: Long = SERVICE_GRACE_MS,
): Boolean = !hasActive && idleSinceMs != null && (nowMs - idleSinceMs) >= graceMs

fun shouldStartService(
    serviceRunning: Boolean,
    lastStartAtMs: Long?,
    nowMs: Long,
    minGapMs: Long = SERVICE_MIN_GAP_MS,
): Boolean = !serviceRunning && (lastStartAtMs == null || (nowMs - lastStartAtMs) >= minGapMs)
