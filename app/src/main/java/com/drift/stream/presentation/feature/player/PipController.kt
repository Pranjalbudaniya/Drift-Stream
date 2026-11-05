package com.drift.stream.presentation.feature.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.drift.stream.R
import com.drift.stream.data.player.PlayerTrace
import com.drift.stream.domain.usecase.PipAction
import com.drift.stream.domain.usecase.pipAspect

class PipController(
    private val activity: ComponentActivity,
    onEvent: (PlayerEvent) -> Unit,
) {
    val supported: Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private var lastParams: PictureInPictureParams? = null
    private var isReceiverRegistered = false
    private var currentSeekStepMs: Long = 10_000L

    private val receiver = createReceiver(onEvent)

    init {
        if (supported) {
            ContextCompat.registerReceiver(
                activity,
                receiver,
                IntentFilter(ACTION_PIP),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            isReceiverRegistered = true
        }
    }

    fun update(
        wants: Boolean,
        aspect: Float?,
        actions: List<PipAction>,
        videoBounds: Rect?,
    ) {
        if (!supported) return

        val (num, den) = pipAspect(aspect)
        PlayerTrace.log("pip.params", "wants" to wants, "aspect" to "$num/$den")

        val backAction = actions.filterIsInstance<PipAction.Back>().firstOrNull()
        if (backAction != null) {
            currentSeekStepMs = backAction.seekStepSeconds * MILLIS_PER_SECOND
        }

        val remoteActions =
            actions.take(activity.maxNumPictureInPictureActions).map { action ->
                createRemoteAction(action)
            }

        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(Rational(num, den))
                .setAutoEnterEnabled(wants)
                .setSeamlessResizeEnabled(false)
                .setActions(remoteActions)

        if (videoBounds != null) {
            builder.setSourceRectHint(videoBounds)
        }

        val params = builder.build()
        lastParams = params
        try {
            activity.setPictureInPictureParams(params)
        } catch (_: IllegalStateException) {
            // Activity may not be in a state where params can be set
        }
    }

    fun enterNow(): Boolean {
        if (!supported) return false
        val params =
            lastParams ?: run {
                val (num, den) = pipAspect(null)
                PictureInPictureParams
                    .Builder()
                    .setAspectRatio(Rational(num, den))
                    .build()
            }
        val result =
            try {
                activity.enterPictureInPictureMode(params)
            } catch (_: IllegalStateException) {
                false
            }
        PlayerTrace.log("pip.enter", "result" to result)
        return result
    }

    fun release() {
        if (!supported) return
        update(wants = false, aspect = null, actions = emptyList(), videoBounds = null)
        if (isReceiverRegistered) {
            try {
                activity.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if already unregistered
            }
            isReceiverRegistered = false
        }
    }

    private fun createReceiver(onEvent: (PlayerEvent) -> Unit): BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                val kind = intent?.getStringExtra(EXTRA_KIND) ?: return
                PlayerTrace.log("pip.action", "kind" to kind)
                when (kind) {
                    KIND_BACK -> onEvent(PlayerEvent.SeekBy(-currentSeekStepMs))
                    KIND_PLAY_PAUSE -> onEvent(PlayerEvent.TogglePlay)
                    KIND_FORWARD -> onEvent(PlayerEvent.SeekBy(currentSeekStepMs))
                }
            }
        }

    private fun createRemoteAction(action: PipAction): RemoteAction =
        if (action is PipAction.Back) {
            createBackAction(action)
        } else if (action is PipAction.PlayPause) {
            createPlayPauseAction(action)
        } else {
            createForwardAction(action as PipAction.Forward)
        }

    private fun createBackAction(action: PipAction.Back): RemoteAction {
        val intent = createActionIntent(KIND_BACK, REQUEST_BACK)
        val icon = Icon.createWithResource(activity, R.drawable.ic_pip_back)
        val title = activity.getString(R.string.pip_action_back)
        val desc = activity.getString(R.string.pip_action_back_desc, action.seekStepSeconds)
        return RemoteAction(icon, title, desc, intent)
    }

    private fun createPlayPauseAction(action: PipAction.PlayPause): RemoteAction {
        val intent = createActionIntent(KIND_PLAY_PAUSE, REQUEST_PLAY_PAUSE)
        val iconRes = if (action.showPause) R.drawable.ic_pip_pause else R.drawable.ic_pip_play
        val icon = Icon.createWithResource(activity, iconRes)
        val strRes = if (action.showPause) R.string.player_pause else R.string.player_play
        val text = activity.getString(strRes)
        return RemoteAction(icon, text, text, intent)
    }

    private fun createForwardAction(action: PipAction.Forward): RemoteAction {
        val intent = createActionIntent(KIND_FORWARD, REQUEST_FORWARD)
        val icon = Icon.createWithResource(activity, R.drawable.ic_pip_forward)
        val title = activity.getString(R.string.pip_action_forward)
        val desc = activity.getString(R.string.pip_action_forward_desc, action.seekStepSeconds)
        return RemoteAction(icon, title, desc, intent)
    }

    private fun createActionIntent(
        kind: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(ACTION_PIP).apply {
                setPackage(activity.packageName)
                putExtra(EXTRA_KIND, kind)
            }
        return PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        const val ACTION_PIP = "com.drift.stream.PIP_ACTION"
        const val EXTRA_KIND = "kind"
        const val KIND_BACK = "BACK"
        const val KIND_PLAY_PAUSE = "PLAY_PAUSE"
        const val KIND_FORWARD = "FORWARD"
        private const val REQUEST_BACK = 1
        private const val REQUEST_PLAY_PAUSE = 2
        private const val REQUEST_FORWARD = 3
    }
}
