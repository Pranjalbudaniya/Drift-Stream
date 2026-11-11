package com.drift.stream.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.domain.profile.StartGate
import com.drift.stream.presentation.common.OneShot
import com.drift.stream.presentation.feature.player.PlaybackSessionHolder
import com.drift.stream.presentation.feature.profile.AddProfileScreen
import com.drift.stream.presentation.feature.profile.ProfileSetupScreen
import com.drift.stream.presentation.feature.profile.WhoIsWatchingScreen

@Composable
fun AppGate(
    deepLinkHolder: OneShot<String>,
    openDownloadsHolder: OneShot<Unit>,
    playbackSessionHolder: PlaybackSessionHolder,
    gateViewModel: AppGateViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by gateViewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.gate) {
        is StartGate.Resolving -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
        }

        is StartGate.Setup -> {
            ProfileSetupScreen(
                onDone = { ownerId ->
                    gateViewModel.onSetupCompleted(ownerId)
                },
                modifier = modifier,
            )
        }

        is StartGate.Picker -> {
            var adding by rememberSaveable { mutableStateOf(false) }
            var formKey by rememberSaveable { mutableIntStateOf(0) }

            if (adding) {
                AddProfileScreen(
                    formKey = formKey,
                    onBack = { adding = false },
                    onDone = { adding = false },
                    modifier = modifier,
                )
            } else {
                WhoIsWatchingScreen(
                    profiles = uiState.profiles,
                    isChoosing = uiState.isChoosing,
                    chooseFailed = uiState.chooseFailed,
                    onProfileChosen = { id ->
                        gateViewModel.onProfileChosen(id)
                    },
                    onAddProfile = {
                        formKey++
                        adding = true
                    },
                    modifier = modifier,
                )
            }
        }

        is StartGate.Enter -> {
            NexusApp(
                deepLinkHolder = deepLinkHolder,
                openDownloadsHolder = openDownloadsHolder,
                playbackSessionHolder = playbackSessionHolder,
            )
        }
    }
}
