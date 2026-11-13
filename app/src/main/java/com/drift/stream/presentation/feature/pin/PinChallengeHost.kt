package com.drift.stream.presentation.feature.pin

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.domain.profile.PinChallenges

@Composable
fun PinChallengeHost(
    challenges: PinChallenges,
    modifier: Modifier = Modifier,
) {
    val pending by challenges.pending.collectAsStateWithLifecycle()

    val currentPending = pending
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
            modifier = modifier,
        )
    }
}
