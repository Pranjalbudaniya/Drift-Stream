package com.drift.stream.domain.usecase

const val WATCHDOG_UNHEALTHY_THRESHOLD_MS = 2000L
const val WATCHDOG_TICK_INTERVAL_MS = 500L

data class VideoHealth(
    val isPlaying: Boolean,
    val hasVideoTrack: Boolean,
    val voutCount: Int,
    val hardwareDecoding: Boolean,
)

sealed interface WatchdogAction {
    data object None : WatchdogAction

    data object Reattach : WatchdogAction

    data object RestartInSoftware : WatchdogAction
}

class VideoWatchdog {
    private var unhealthySinceMs: Long? = null
    private var reattachAttempted: Boolean = false
    private var softwareRestartAttempted: Boolean = false

    fun onTick(
        nowMs: Long,
        input: VideoHealth,
    ): WatchdogAction {
        if (!input.isPlaying || !input.hasVideoTrack || input.voutCount > 0) {
            unhealthySinceMs = null
            return WatchdogAction.None
        }

        val startTime = unhealthySinceMs ?: nowMs.also { unhealthySinceMs = it }
        val elapsed = nowMs - startTime

        return when {
            !reattachAttempted && elapsed >= WATCHDOG_UNHEALTHY_THRESHOLD_MS -> {
                reattachAttempted = true
                unhealthySinceMs = nowMs
                WatchdogAction.Reattach
            }

            reattachAttempted && !softwareRestartAttempted && elapsed >= WATCHDOG_UNHEALTHY_THRESHOLD_MS -> {
                softwareRestartAttempted = true
                if (input.hardwareDecoding) {
                    WatchdogAction.RestartInSoftware
                } else {
                    WatchdogAction.None
                }
            }

            else -> {
                WatchdogAction.None
            }
        }
    }

    fun onNewMedia() {
        unhealthySinceMs = null
        reattachAttempted = false
        softwareRestartAttempted = false
    }
}
