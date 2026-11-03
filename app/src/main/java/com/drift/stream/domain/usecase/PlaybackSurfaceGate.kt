package com.drift.stream.domain.usecase

sealed interface GateAction {
    data class StartPlayback(
        val url: String,
    ) : GateAction

    data object PausePlayback : GateAction

    data object DetachVideo : GateAction

    data object AttachVideo : GateAction

    data object DetachPreviousThenAttach : GateAction

    data object NudgeFrame : GateAction
}

class PlaybackSurfaceGate {
    private var attachedLayoutId: Int? = null
    private var pendingUrl: String? = null
    private var isForeground: Boolean = true
    private var detachedByBackground: Boolean = false

    fun onPlayRequested(url: String): List<GateAction> =
        if (attachedLayoutId != null && isForeground) {
            listOf(GateAction.StartPlayback(url))
        } else {
            pendingUrl = url
            emptyList()
        }

    fun onSurfaceAttached(layoutId: Int): List<GateAction> {
        val actions = mutableListOf<GateAction>()
        if (attachedLayoutId == layoutId) {
            return emptyList()
        }

        if (attachedLayoutId != null) {
            actions.add(GateAction.DetachPreviousThenAttach)
        }
        attachedLayoutId = layoutId

        val pending = pendingUrl
        if (pending != null && isForeground) {
            actions.add(GateAction.StartPlayback(pending))
            pendingUrl = null
        }
        return actions
    }

    fun onSurfaceDetached(layoutId: Int): List<GateAction> {
        if (attachedLayoutId == layoutId) {
            attachedLayoutId = null
        }
        return emptyList()
    }

    fun onBackground(isPlaying: Boolean): List<GateAction> {
        isForeground = false
        val actions = mutableListOf<GateAction>()
        if (isPlaying) {
            actions.add(GateAction.PausePlayback)
        }
        if (attachedLayoutId != null) {
            actions.add(GateAction.DetachVideo)
            detachedByBackground = true
        }
        return actions
    }

    fun onForeground(
        mediaLoaded: Boolean,
        isPlaying: Boolean,
    ): List<GateAction> {
        if (isForeground) {
            return emptyList()
        }
        isForeground = true
        val actions = mutableListOf<GateAction>()

        if (attachedLayoutId != null && detachedByBackground) {
            actions.add(GateAction.AttachVideo)
            detachedByBackground = false
        }

        if (mediaLoaded && !isPlaying) {
            actions.add(GateAction.NudgeFrame)
        }

        val pending = pendingUrl
        if (pending != null && attachedLayoutId != null) {
            actions.add(GateAction.StartPlayback(pending))
            pendingUrl = null
        }

        return actions
    }

    fun onReleased() {
        attachedLayoutId = null
        pendingUrl = null
        isForeground = true
        detachedByBackground = false
    }
}
