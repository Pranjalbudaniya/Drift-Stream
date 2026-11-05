package com.drift.stream.domain.usecase

import com.drift.stream.presentation.feature.player.PlayerPhase
import kotlin.math.roundToInt

private const val MIN_ASPECT = 1f / 2.39f
private const val MAX_ASPECT = 2.39f
private const val DEFAULT_ASPECT = 16f / 9f
private const val ASPECT_SCALE = 1000f
private const val DENOMINATOR = 1000

sealed interface PipAction {
    data class Back(
        val seekStepSeconds: Int,
    ) : PipAction

    data class PlayPause(
        val showPause: Boolean,
    ) : PipAction

    data class Forward(
        val seekStepSeconds: Int,
    ) : PipAction
}

fun pipAspect(aspect: Float?): Pair<Int, Int> {
    val a =
        if (aspect == null || aspect <= 0f || !aspect.isFinite()) {
            DEFAULT_ASPECT
        } else {
            aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        }
    return Pair((a * ASPECT_SCALE).roundToInt(), DENOMINATOR)
}

fun PlayerPhase.wantsPip(
    popUpOnHome: Boolean,
    onPlayerScreen: Boolean,
    hasMedia: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
): Boolean = popUpOnHome && onPlayerScreen && hasMedia && this is PlayerPhase.Playing && (isPlaying || isBuffering)

fun pipActions(
    isPlaying: Boolean,
    seekStepSeconds: Int,
): List<PipAction> =
    listOf(
        PipAction.Back(seekStepSeconds),
        PipAction.PlayPause(showPause = isPlaying),
        PipAction.Forward(seekStepSeconds),
    )
