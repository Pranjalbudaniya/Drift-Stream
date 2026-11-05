package com.drift.stream.domain.usecase

import com.drift.stream.presentation.feature.player.PlayerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipRulesTest {
    @Test
    fun PR1_pipAspect_nullOrInvalid_returns16by9() {
        assertEquals(Pair(1778, 1000), pipAspect(null))
        assertEquals(Pair(1778, 1000), pipAspect(Float.NaN))
        assertEquals(Pair(1778, 1000), pipAspect(0f))
        assertEquals(Pair(1778, 1000), pipAspect(-1f))
    }

    @Test
    fun PR2_pipAspect_16by9_returnsRoundedRational() {
        assertEquals(Pair(1778, 1000), pipAspect(16f / 9f))
        assertEquals(Pair(1000, 1000), pipAspect(1f))
    }

    @Test
    fun PR3_pipAspect_tooWide_isClampedTo239() {
        assertEquals(Pair(2390, 1000), pipAspect(3.5f))
    }

    @Test
    fun PR4_pipAspect_tooTall_isClampedToInverse239() {
        assertEquals(Pair(418, 1000), pipAspect(0.2f))
    }

    @Test
    fun PR5_wantsPip_trueOnlyWhenAllConditionsHold() {
        // Base case: all conditions true -> true
        assertTrue(
            PlayerPhase.Playing.wantsPip(
                pipOnHome = true,
                onPlayerScreen = true,
                hasMedia = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )

        // Setting off
        assertFalse(
            PlayerPhase.Playing.wantsPip(
                pipOnHome = false,
                onPlayerScreen = true,
                hasMedia = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )

        // Not on player screen
        assertFalse(
            PlayerPhase.Playing.wantsPip(
                pipOnHome = true,
                onPlayerScreen = false,
                hasMedia = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )

        // No media
        assertFalse(
            PlayerPhase.Playing.wantsPip(
                pipOnHome = true,
                onPlayerScreen = true,
                hasMedia = false,
                isPlaying = true,
                isBuffering = false,
            ),
        )

        // Phase not Playing
        assertFalse(
            PlayerPhase.Empty.wantsPip(
                pipOnHome = true,
                onPlayerScreen = true,
                hasMedia = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )

        // Neither playing nor buffering
        assertFalse(
            PlayerPhase.Playing.wantsPip(
                pipOnHome = true,
                onPlayerScreen = true,
                hasMedia = true,
                isPlaying = false,
                isBuffering = false,
            ),
        )
    }

    @Test
    fun PR6_wantsPip_bufferingCounts() {
        assertTrue(
            PlayerPhase.Playing.wantsPip(
                pipOnHome = true,
                onPlayerScreen = true,
                hasMedia = true,
                isPlaying = false,
                isBuffering = true,
            ),
        )
    }

    @Test
    fun PR7_pipActions_playingShowsPauseAndSteps() {
        val actions = pipActions(isPlaying = true, seekStepSeconds = 10)
        val expected =
            listOf(
                PipAction.Back(10),
                PipAction.PlayPause(showPause = true),
                PipAction.Forward(10),
            )
        assertEquals(expected, actions)
    }

    @Test
    fun PR8_pipActions_pausedShowsPlay() {
        val actions = pipActions(isPlaying = false, seekStepSeconds = 30)
        val expected =
            listOf(
                PipAction.Back(30),
                PipAction.PlayPause(showPause = false),
                PipAction.Forward(30),
            )
        assertEquals(expected, actions)
    }
}
