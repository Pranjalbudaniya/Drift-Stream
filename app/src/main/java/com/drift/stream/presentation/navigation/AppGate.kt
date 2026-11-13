package com.drift.stream.presentation.navigation

import androidx.compose.foundation.layout.Box
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
import com.drift.stream.domain.profile.DeleteHost
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.StartGate
import com.drift.stream.presentation.common.OneShot
import com.drift.stream.presentation.feature.pin.PinChallengeHost
import com.drift.stream.presentation.feature.player.PlaybackSessionHolder
import com.drift.stream.presentation.feature.profile.AddProfileScreen
import com.drift.stream.presentation.feature.profile.EditProfileScreen
import com.drift.stream.presentation.feature.profile.ProfileSetupScreen
import com.drift.stream.presentation.feature.profile.WhoIsWatchingScreen

@Composable
fun AppGate(
    deepLinkHolder: OneShot<String>,
    openDownloadsHolder: OneShot<Unit>,
    playbackSessionHolder: PlaybackSessionHolder,
    gateViewModel: AppGateViewModel,
    pinChallenges: PinChallenges,
    modifier: Modifier = Modifier,
) {
    val uiState by gateViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState.gate) {
            is StartGate.Resolving -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {}
            }

            is StartGate.Setup -> {
                ProfileSetupScreen(
                    onDone = { ownerId ->
                        gateViewModel.onSetupCompleted(ownerId)
                    },
                )
            }

            is StartGate.Picker -> {
                var adding by rememberSaveable { mutableStateOf(false) }
                var formKey by rememberSaveable { mutableIntStateOf(0) }
                var editingProfileId by rememberSaveable { mutableStateOf(0L) }

                val editingProfileExists = uiState.profiles.any { it.id == editingProfileId }
                val currentEditingId = if (editingProfileExists) editingProfileId else 0L
                if (editingProfileId != 0L && !editingProfileExists) {
                    editingProfileId = 0L
                }

                if (currentEditingId > 0L) {
                    EditProfileScreen(
                        profileId = currentEditingId,
                        host = DeleteHost.Picker,
                        onBack = { editingProfileId = 0L },
                        onDone = { editingProfileId = 0L },
                        onDelete = { editingProfileId = 0L },
                    )
                } else if (adding) {
                    AddProfileScreen(
                        formKey = formKey,
                        onBack = { adding = false },
                        onDone = { adding = false },
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
                        onEditProfile = { id ->
                            editingProfileId = id
                        },
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

        PinChallengeHost(challenges = pinChallenges)
    }
}
