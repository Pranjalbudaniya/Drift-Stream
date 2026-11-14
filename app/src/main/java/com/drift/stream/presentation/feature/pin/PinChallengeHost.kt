package com.drift.stream.presentation.feature.pin

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.domain.profile.PinChallenges

private const val FADE_DURATION_MS = 150

@Composable
fun PinChallengeHost(
    challenges: PinChallenges,
    modifier: Modifier = Modifier,
) {
    val pending by challenges.pending.collectAsStateWithLifecycle()
    var activePending by remember { mutableStateOf<PendingPin?>(null) }
    if (pending != null) {
        activePending = pending
    }

    AnimatedVisibility(
        visible = pending != null,
        enter = fadeIn(animationSpec = tween(durationMillis = FADE_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(durationMillis = FADE_DURATION_MS)),
        modifier = modifier,
    ) {
        val currentPending = activePending
        if (currentPending != null) {
            val context = LocalContext.current
            DisposableEffect(currentPending.id) {
                val activity =
                    (context as? Activity)
                        ?: ((context as? ContextWrapper)?.baseContext as? Activity)
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            PinPadScreen(
                pendingPin = currentPending,
            )
        }
    }
}
