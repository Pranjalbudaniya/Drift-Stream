package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSurfaceGateTest {
    @Test
    fun SG1_play_beforeAttach_isDeferred_thenStartsOnAttach() {
        val gate = PlaybackSurfaceGate()
        val actionsBefore = gate.onPlayRequested("http://example.com/video.mp4")
        assertEquals(emptyList<GateAction>(), actionsBefore)

        val actionsAfter = gate.onSurfaceAttached(1)
        assertEquals(listOf(GateAction.StartPlayback("http://example.com/video.mp4")), actionsAfter)
    }

    @Test
    fun SG2_play_afterAttach_startsImmediately() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)

        val actions = gate.onPlayRequested("http://example.com/video.mp4")
        assertEquals(listOf(GateAction.StartPlayback("http://example.com/video.mp4")), actions)
    }

    @Test
    fun SG3_attachTwice_sameLayout_isIgnored() {
        val gate = PlaybackSurfaceGate()
        val first = gate.onSurfaceAttached(1)
        assertEquals(emptyList<GateAction>(), first)

        val second = gate.onSurfaceAttached(1)
        assertEquals(emptyList<GateAction>(), second)
    }

    @Test
    fun SG4_playInBackground_isDeferredUntilForeground() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)
        gate.onBackground(isPlaying = false)

        val actionsPlay = gate.onPlayRequested("http://example.com/video.mp4")
        assertEquals(emptyList<GateAction>(), actionsPlay)

        val actionsForeground = gate.onForeground(mediaLoaded = true, isPlaying = false)
        assertTrue(actionsForeground.contains(GateAction.AttachVideo))
        assertTrue(actionsForeground.contains(GateAction.StartPlayback("http://example.com/video.mp4")))
    }

    @Test
    fun SG5_background_pausesAndDetaches_foreground_attachesAgain() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)

        val bgActions = gate.onBackground(isPlaying = true)
        assertEquals(listOf(GateAction.PausePlayback, GateAction.DetachVideo), bgActions)

        val fgActions = gate.onForeground(mediaLoaded = true, isPlaying = false)
        assertEquals(listOf(GateAction.AttachVideo, GateAction.NudgeFrame), fgActions)
    }

    @Test
    fun SG6_foreground_withoutPriorBackground_doesNothing() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)

        val actions = gate.onForeground(mediaLoaded = true, isPlaying = false)
        assertEquals(emptyList<GateAction>(), actions)
    }

    @Test
    fun SG7_background_whenNotPlaying_onlyDetaches() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)

        val actions = gate.onBackground(isPlaying = false)
        assertEquals(listOf(GateAction.DetachVideo), actions)
    }

    @Test
    fun SG8_foreground_whenPaused_nudgesTheFrame_andNeverStartsPlayback() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)
        gate.onBackground(isPlaying = true)

        val actions = gate.onForeground(mediaLoaded = true, isPlaying = false)
        assertTrue(actions.contains(GateAction.NudgeFrame))
        assertFalse(actions.any { it is GateAction.StartPlayback })
    }

    @Test
    fun SG9_foreground_whenPlaying_doesNotNudge() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)
        gate.onBackground(isPlaying = true)

        val actions = gate.onForeground(mediaLoaded = true, isPlaying = true)
        assertEquals(listOf(GateAction.AttachVideo), actions)
        assertFalse(actions.contains(GateAction.NudgeFrame))
    }

    @Test
    fun SG10_newLayout_replacesTheOldOne() {
        val gate = PlaybackSurfaceGate()
        gate.onSurfaceAttached(1)

        val actions = gate.onSurfaceAttached(2)
        assertEquals(listOf(GateAction.DetachPreviousThenAttach), actions)

        val detachOld = gate.onSurfaceDetached(1)
        assertEquals(emptyList<GateAction>(), detachOld)
    }

    @Test
    fun SG11_release_clearsPendingWork() {
        val gate = PlaybackSurfaceGate()
        gate.onPlayRequested("http://example.com/video.mp4")
        gate.onReleased()

        val actions = gate.onSurfaceAttached(1)
        assertEquals(emptyList<GateAction>(), actions)
    }
}
