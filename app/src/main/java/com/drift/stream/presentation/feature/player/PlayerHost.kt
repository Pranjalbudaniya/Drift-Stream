package com.drift.stream.presentation.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.drift.stream.R
import com.drift.stream.data.player.PlayerTrace
import com.drift.stream.domain.usecase.PipAction
import com.drift.stream.presentation.feature.streams.ExternalActions

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
internal fun PipModeListener(
    componentActivity: ComponentActivity?,
    onPipChanged: (Boolean) -> Unit,
) {
    DisposableEffect(componentActivity) {
        val listener =
            Consumer<PictureInPictureModeChangedInfo> { info ->
                onPipChanged(info.isInPictureInPictureMode)
            }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }
}

@Composable
internal fun PipLifecycle(
    pipController: PipController?,
    wantsPip: Boolean,
    aspect: Float?,
    actions: List<PipAction>,
    videoBounds: Rect?,
) {
    DisposableEffect(pipController) {
        onDispose {
            pipController?.release()
        }
    }

    LaunchedEffect(pipController, wantsPip, aspect, actions, videoBounds) {
        pipController?.update(
            wants = wantsPip,
            aspect = aspect,
            actions = actions,
            videoBounds = videoBounds,
        )
    }
}

@Composable
internal fun PlayerScreenEffects(
    viewModel: PlayerViewModel,
    context: Context,
    onSwitchToNext: () -> Unit,
    onChooseSource: (PlayerEffect.ChooseNextSource) -> Unit,
) {
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PlayerEffect.OpenExternal -> {
                    ExternalActions.openVideo(
                        context = context,
                        url = effect.url,
                        title = effect.title,
                        headers = emptyMap(),
                        onError = {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.player_external_no_app),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                    )
                }

                PlayerEffect.SwitchToNext -> {
                    onSwitchToNext()
                }

                is PlayerEffect.ChooseNextSource -> {
                    onChooseSource(effect)
                }
            }
        }
    }
}

@Composable
internal fun PlayerLifecycleObserver(
    lifecycleOwner: LifecycleOwner,
    onEvent: (PlayerEvent) -> Unit,
) {
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                PlayerTrace.log("life", "e" to event.name)
                if (event == Lifecycle.Event.ON_START) {
                    onEvent(PlayerEvent.AppStarted)
                } else if (event == Lifecycle.Event.ON_STOP) {
                    onEvent(PlayerEvent.AppStopped)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
internal fun PlayerKeepScreenOn(
    window: Window?,
    shouldKeepScreenOn: Boolean,
) {
    DisposableEffect(shouldKeepScreenOn, window) {
        if (window != null) {
            if (shouldKeepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

internal fun restorePlayerChrome(
    activity: Activity?,
    window: Window?,
) {
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }
}

internal fun triggerPip(
    pipController: PipController?,
    context: Context,
) {
    val entered = pipController?.enterNow() == true
    if (!entered) {
        Toast.makeText(context, R.string.pip_pop_up_unavailable, Toast.LENGTH_SHORT).show()
    }
}
