package com.drift.stream.data.player

import android.content.Context
import android.net.Uri
import android.view.View
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.usecase.GateAction
import com.drift.stream.domain.usecase.PlaybackSurfaceGate
import com.drift.stream.domain.usecase.VideoHealth
import com.drift.stream.domain.usecase.VideoWatchdog
import com.drift.stream.domain.usecase.WATCHDOG_TICK_INTERVAL_MS
import com.drift.stream.domain.usecase.WatchdogAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val BUFFERING_COMPLETE_PERCENT = 100f
private const val MICROS_PER_MILLI = 1000L
private const val SLAVE_TYPE_SUBTITLE = 0

@Suppress("TooManyFunctions")
class LibVlcPlayer(
    context: Context,
    io: CoroutineDispatcher,
    options: List<String> = emptyList(),
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val libVlc = LibVLC(context, ArrayList(options))
    private val mediaPlayer = MediaPlayer(libVlc)

    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    private val _tracks = MutableStateFlow(PlayerTracks())
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    private var currentMedia: Media? = null
    private var currentMediaUrl: String? = null
    private var currentLayout: VLCVideoLayout? = null
    private var currentVoutCount: Int = 0
    private var hardwareDecoding: Boolean = true
    private val gate = PlaybackSurfaceGate()
    private val watchdog = VideoWatchdog()
    private var watchdogJob: Job? = null
    private val released = AtomicBoolean(false)

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    refreshTracks()
                    _snapshot.update {
                        it.copy(isPlaying = true, isBuffering = false, rate = mediaPlayer.rate)
                    }
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "Playing",
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                    startWatchdogTicker()
                }

                MediaPlayer.Event.Paused -> {
                    stopWatchdogTicker()
                    _snapshot.update {
                        it.copy(isPlaying = false, rate = mediaPlayer.rate)
                    }
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "Paused",
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                }

                MediaPlayer.Event.Stopped -> {
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "Stopped",
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                }

                MediaPlayer.Event.Buffering -> {
                    val isBuffering = event.buffering < BUFFERING_COMPLETE_PERCENT
                    _snapshot.update {
                        it.copy(isBuffering = isBuffering, rate = mediaPlayer.rate)
                    }
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "Buffering",
                        "pct" to event.buffering,
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                }

                MediaPlayer.Event.EncounteredError -> {
                    stopWatchdogTicker()
                    currentVoutCount = 0
                    _snapshot.update {
                        it.copy(failed = true, isPlaying = false, isBuffering = false, rate = mediaPlayer.rate)
                    }
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "EncounteredError",
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                }

                MediaPlayer.Event.EndReached -> {
                    stopWatchdogTicker()
                    currentVoutCount = 0
                    _snapshot.update {
                        it.copy(ended = true, isPlaying = false, isBuffering = false, rate = mediaPlayer.rate)
                    }
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "EndReached",
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                }

                MediaPlayer.Event.Vout -> {
                    currentVoutCount = event.voutCount
                    PlayerTrace.log(
                        "vlc.event",
                        "type" to "Vout",
                        "voutCount" to event.voutCount,
                        "playerState" to mediaPlayer.playerState,
                        "attached" to mediaPlayer.vlcVout.areViewsAttached(),
                        "snapPlaying" to _snapshot.value.isPlaying,
                    )
                }

                MediaPlayer.Event.LengthChanged -> {
                    _snapshot.update {
                        it.copy(durationMs = event.lengthChanged, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.TimeChanged -> {
                    _snapshot.update {
                        it.copy(positionMs = event.timeChanged, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.SeekableChanged -> {
                    _snapshot.update {
                        it.copy(isSeekable = event.seekable, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESDeleted,
                MediaPlayer.Event.ESSelected,
                -> {
                    refreshTracks()
                }
            }
        }
    }

    private fun refreshTracks() {
        val rawAudio = mediaPlayer.audioTracks ?: emptyArray()
        val audioList =
            rawAudio
                .filter { it.id != -1 }
                .map { PlayerTrack(id = it.id.toString(), name = it.name.orEmpty()) }

        val rawSpu = mediaPlayer.spuTracks ?: emptyArray()
        val spuList =
            rawSpu
                .filter { it.id != -1 }
                .map { PlayerTrack(id = it.id.toString(), name = it.name.orEmpty()) }

        val currentAudioId = mediaPlayer.audioTrack
        val selectedAudio = if (currentAudioId == -1) null else currentAudioId.toString()

        val currentSpuId = mediaPlayer.spuTrack
        val selectedSpu = if (currentSpuId == -1) null else currentSpuId.toString()

        _tracks.value =
            PlayerTracks(
                audio = audioList,
                subtitles = spuList,
                selectedAudioId = selectedAudio,
                selectedSubtitleId = selectedSpu,
            )
    }

    override fun createSurface(context: Context): View {
        val layout = VLCVideoLayout(context)
        currentLayout = layout
        PlayerTrace.log("vlc.surface", "op" to "attach", "layoutId" to System.identityHashCode(layout))
        mediaPlayer.attachViews(layout, null, true, false)
        val actions = gate.onSurfaceAttached(System.identityHashCode(layout))
        executeGateActions(actions)
        return layout
    }

    override fun detachSurface() {
        val layout = currentLayout
        PlayerTrace.log("vlc.surface", "op" to "detach", "layoutId" to layout?.let { System.identityHashCode(it) })
        mediaPlayer.vlcVout.detachViews()
        if (layout != null) {
            val actions = gate.onSurfaceDetached(System.identityHashCode(layout))
            executeGateActions(actions)
        }
    }

    override fun play(url: String) {
        val actions = gate.onPlayRequested(url)
        executeGateActions(actions)
    }

    private fun startPlaybackInternal(url: String) {
        currentMediaUrl = url
        currentVoutCount = 0
        hardwareDecoding = true
        watchdog.onNewMedia()
        val oldMedia = currentMedia
        val media = Media(libVlc, Uri.parse(url))
        currentMedia = media
        mediaPlayer.media = media
        oldMedia?.release()
        _snapshot.update {
            it.copy(failed = false, ended = false)
        }
        mediaPlayer.play()
    }

    private fun executeGateActions(actions: List<GateAction>) {
        for (action in actions) {
            PlayerTrace.log("vlc.gate", "action" to action::class.java.simpleName)
            when (action) {
                is GateAction.StartPlayback -> {
                    startPlaybackInternal(action.url)
                }

                GateAction.PausePlayback -> {
                    mediaPlayer.pause()
                }

                GateAction.DetachVideo -> {
                    mediaPlayer.vlcVout.detachViews()
                }

                GateAction.AttachVideo -> {
                    currentLayout?.let { mediaPlayer.attachViews(it, null, true, false) }
                }

                GateAction.DetachPreviousThenAttach -> {
                    mediaPlayer.vlcVout.detachViews()
                    currentLayout?.let { mediaPlayer.attachViews(it, null, true, false) }
                }

                GateAction.NudgeFrame -> {
                    mediaPlayer.time = mediaPlayer.time
                }
            }
        }
    }

    override fun pause() {
        PlayerTrace.log(
            "vlc.pause",
            "playerState" to mediaPlayer.playerState,
            "playing" to mediaPlayer.isPlaying,
            "attached" to mediaPlayer.vlcVout.areViewsAttached(),
        )
        mediaPlayer.pause()
    }

    override fun resume() {
        PlayerTrace.log(
            "vlc.resume",
            "playerState" to mediaPlayer.playerState,
            "playing" to mediaPlayer.isPlaying,
            "attached" to mediaPlayer.vlcVout.areViewsAttached(),
        )
        mediaPlayer.play()
    }

    override fun seekTo(positionMs: Long) {
        PlayerTrace.log(
            "vlc.seek",
            "playerState" to mediaPlayer.playerState,
            "playing" to mediaPlayer.isPlaying,
            "attached" to mediaPlayer.vlcVout.areViewsAttached(),
        )
        mediaPlayer.time = positionMs
    }

    override fun setRate(rate: Float) {
        mediaPlayer.rate = rate
        _snapshot.update { it.copy(rate = mediaPlayer.rate) }
    }

    override fun selectAudioTrack(id: String) {
        id.toIntOrNull()?.let { intId ->
            mediaPlayer.setAudioTrack(intId)
            refreshTracks()
        }
    }

    override fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            mediaPlayer.setSpuTrack(-1)
        } else {
            id.toIntOrNull()?.let { intId ->
                mediaPlayer.setSpuTrack(intId)
            }
        }
        refreshTracks()
    }

    override fun addSubtitle(uri: String) {
        mediaPlayer.addSlave(SLAVE_TYPE_SUBTITLE, Uri.parse(uri), true)
    }

    override fun setSubtitleDelay(ms: Long) {
        mediaPlayer.setSpuDelay(ms * MICROS_PER_MILLI)
    }

    override fun setVideoScale(scale: VideoScale) {
        val scaleType =
            when (scale) {
                VideoScale.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                VideoScale.CROP -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                VideoScale.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
                VideoScale.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
                VideoScale.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
                VideoScale.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            }
        mediaPlayer.videoScale = scaleType
    }

    override fun onAppBackgrounded() {
        val isPlaying = _snapshot.value.isPlaying
        PlayerTrace.log("vlc.bg", "snapPlaying" to isPlaying, "mediaLoaded" to (currentMedia != null))
        val actions = gate.onBackground(isPlaying)
        executeGateActions(actions)
    }

    override fun onAppForegrounded() {
        val mediaLoaded = currentMedia != null
        val isPlaying = _snapshot.value.isPlaying
        PlayerTrace.log("vlc.fg", "snapPlaying" to isPlaying, "mediaLoaded" to mediaLoaded)
        val actions = gate.onForeground(mediaLoaded, isPlaying)
        executeGateActions(actions)
    }

    private fun startWatchdogTicker() {
        watchdogJob?.cancel()
        watchdogJob =
            scope.launch {
                while (isActive) {
                    delay(WATCHDOG_TICK_INTERVAL_MS)
                    val isPlaying = _snapshot.value.isPlaying
                    if (!isPlaying) break
                    val hasVideoTrack = mediaPlayer.videoTracksCount > 0
                    val health =
                        VideoHealth(
                            isPlaying = isPlaying,
                            hasVideoTrack = hasVideoTrack,
                            voutCount = currentVoutCount,
                            hardwareDecoding = hardwareDecoding,
                        )
                    val action = watchdog.onTick(System.currentTimeMillis(), health)
                    handleWatchdogAction(action)
                }
            }
    }

    private fun stopWatchdogTicker() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun handleWatchdogAction(action: WatchdogAction) {
        if (action != WatchdogAction.None) {
            PlayerTrace.log("vlc.watchdog", "action" to action::class.java.simpleName)
        }
        when (action) {
            WatchdogAction.None -> {}

            WatchdogAction.Reattach -> {
                currentLayout?.let {
                    mediaPlayer.vlcVout.detachViews()
                    mediaPlayer.attachViews(it, null, true, false)
                }
            }

            WatchdogAction.RestartInSoftware -> {
                restartInSoftware()
            }
        }
    }

    private fun restartInSoftware() {
        val url = currentMediaUrl ?: return
        val currentPos = mediaPlayer.time
        val wasPlaying = _snapshot.value.isPlaying
        hardwareDecoding = false

        val oldMedia = currentMedia
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(false, false)
        currentMedia = media
        mediaPlayer.media = media
        oldMedia?.release()

        if (currentPos > 0) {
            mediaPlayer.time = currentPos
        }
        if (wasPlaying) {
            mediaPlayer.play()
        }
    }

    override fun release() {
        if (released.compareAndSet(false, true)) {
            stopWatchdogTicker()
            gate.onReleased()
            currentLayout = null
            mediaPlayer.setEventListener(null)
            mediaPlayer.vlcVout.detachViews()
            currentMedia?.release()
            currentMedia = null
            scope.launch {
                mediaPlayer.release()
                libVlc.release()
            }
        }
    }
}

@Singleton
class LibVlcPlayerFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : PlayerControllerFactory {
        override fun create(style: SubtitleRenderStyle): PlayerController =
            LibVlcPlayer(context, io, buildSubtitleOptions(style))
    }
