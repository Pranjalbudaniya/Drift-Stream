package com.drift.stream

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.drift.stream.domain.profile.shouldHoldFirstFrame
import com.drift.stream.presentation.navigation.AppGateViewModel
import com.drift.stream.presentation.theme.ThemeUiState
import com.drift.stream.presentation.theme.ThemeViewModel

fun installFirstFrameHold(
    activity: Activity,
    gateViewModel: AppGateViewModel,
    themeViewModel: ThemeViewModel,
) {
    val contentView = activity.findViewById<View>(android.R.id.content) ?: return
    val startTime = SystemClock.elapsedRealtime()

    val listener =
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val gate = gateViewModel.uiState.value.gate
                val themeReady = themeViewModel.uiState.value is ThemeUiState.Ready
                val elapsed = SystemClock.elapsedRealtime() - startTime

                return if (shouldHoldFirstFrame(gate, themeReady, elapsed)) {
                    false
                } else {
                    if (contentView.viewTreeObserver.isAlive) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    true
                }
            }
        }

    contentView.viewTreeObserver.addOnPreDrawListener(listener)
}
