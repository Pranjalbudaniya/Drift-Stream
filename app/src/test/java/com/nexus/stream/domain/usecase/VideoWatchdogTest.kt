package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoWatchdogTest {
    private fun h(
        playing: Boolean = true,
        videoTrack: Boolean = true,
        vout: Int = 0,
        hw: Boolean = true,
    ) = VideoHealth(
        isPlaying = playing,
        hasVideoTrack = videoTrack,
        voutCount = vout,
        hardwareDecoding = hw,
    )

    @Test
    fun WD1_audioWithVideoTrackAndNoVout_after2s_reattaches() {
        val watchdog = VideoWatchdog()
        val action1 = watchdog.onTick(0L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.None, action1)

        val action2 = watchdog.onTick(2000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.Reattach, action2)
    }

    @Test
    fun WD2_stillNoVoutAfterReattach_after2s_restartsInSoftwareOnce() {
        val watchdog = VideoWatchdog()
        watchdog.onTick(0L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        val reattach = watchdog.onTick(2000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.Reattach, reattach)

        val tick3k = watchdog.onTick(3000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.None, tick3k)

        val restart = watchdog.onTick(4000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.RestartInSoftware, restart)

        val later = watchdog.onTick(5000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.None, later)
    }

    @Test
    fun WD3_voutPresent_noAction_andResetsTheTimer() {
        val watchdog = VideoWatchdog()
        val t0 = watchdog.onTick(0L, h(playing = true, videoTrack = true, vout = 1, hw = true))
        assertEquals(WatchdogAction.None, t0)

        val t1k = watchdog.onTick(1000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.None, t1k)

        val t2500 = watchdog.onTick(2500L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.None, t2500)
    }

    @Test
    fun WD4_noVideoTrack_noAction() {
        val watchdog = VideoWatchdog()
        for (time in 0L..10_000L step 500L) {
            val action = watchdog.onTick(time, h(playing = true, videoTrack = false, vout = 0, hw = true))
            assertEquals(WatchdogAction.None, action)
        }
    }

    @Test
    fun WD5_notPlaying_noAction() {
        val watchdog = VideoWatchdog()
        for (time in 0L..10_000L step 500L) {
            val action = watchdog.onTick(time, h(playing = false, videoTrack = true, vout = 0, hw = true))
            assertEquals(WatchdogAction.None, action)
        }
    }

    @Test
    fun WD6_softwareDecoding_neverRestartsInSoftwareAgain() {
        val watchdog = VideoWatchdog()
        watchdog.onTick(0L, h(playing = true, videoTrack = true, vout = 0, hw = false))
        val action2s = watchdog.onTick(2000L, h(playing = true, videoTrack = true, vout = 0, hw = false))
        assertEquals(WatchdogAction.Reattach, action2s)

        val action4s = watchdog.onTick(4000L, h(playing = true, videoTrack = true, vout = 0, hw = false))
        assertEquals(WatchdogAction.None, action4s)
    }

    @Test
    fun WD7_newMedia_resetsTheWatchdog() {
        val watchdog = VideoWatchdog()
        watchdog.onTick(0L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        watchdog.onTick(2000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        watchdog.onTick(4000L, h(playing = true, videoTrack = true, vout = 0, hw = true))

        watchdog.onNewMedia()

        val action0 = watchdog.onTick(5000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.None, action0)

        val actionReattach = watchdog.onTick(7000L, h(playing = true, videoTrack = true, vout = 0, hw = true))
        assertEquals(WatchdogAction.Reattach, actionReattach)
    }

    @Test
    fun WD8_beforeTwoSeconds_noAction() {
        val watchdog = VideoWatchdog()
        val times = listOf(0L, 500L, 1000L, 1500L, 1999L)
        for (t in times) {
            val action = watchdog.onTick(t, h(playing = true, videoTrack = true, vout = 0, hw = true))
            assertEquals(WatchdogAction.None, action)
        }
    }
}
