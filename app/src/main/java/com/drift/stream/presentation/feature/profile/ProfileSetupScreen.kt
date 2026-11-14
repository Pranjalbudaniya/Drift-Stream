package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.presentation.theme.NexusTheme

@Composable
fun ProfileSetupScreen(
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pinPending by viewModel.pinPending.collectAsStateWithLifecycle()
    val croppingBitmap by viewModel.croppingBitmap.collectAsStateWithLifecycle()
    var isEmojiPickerOpen by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.onPhotoPicked(uri)
                }
            },
        )

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SetupEffect.Done -> {
                    if (effect.pinSaveFailed) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.profile_save_pin_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                    onDone(effect.ownerId)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ProfileSetupContent(
            state = state,
            pinPending = pinPending,
            onEvent = viewModel::onEvent,
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickEmoji = { isEmojiPickerOpen = true },
            modifier = Modifier.fillMaxSize(),
        )

        if (isEmojiPickerOpen) {
            EmojiPickerScreen(
                onEmojiPicked = { emoji ->
                    viewModel.onEvent(ProfileFormEvent.EmojiPicked(emoji))
                },
                onDismiss = { isEmojiPickerOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        val activeBitmap = croppingBitmap
        if (activeBitmap != null) {
            PhotoCropScreen(
                bitmap = activeBitmap,
                onDone = viewModel::onCropDone,
                onCancel = viewModel::onCropCancel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ProfileSetupContent(
    state: ProfileFormState,
    onEvent: (ProfileFormEvent) -> Unit,
    modifier: Modifier = Modifier,
    pinPending: Boolean = state.pinPending,
    onPickPhoto: (() -> Unit)? = null,
    onPickEmoji: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
        ) {
            ProfileForm(
                state = state,
                onEvent = onEvent,
                title = stringResource(R.string.profile_setup_title),
                subtitle = stringResource(R.string.profile_setup_subtitle),
                actionButtonText = stringResource(R.string.profile_form_continue),
                onPickPhoto = onPickPhoto,
                onPickEmoji = onPickEmoji,
                pinLockContent = {
                    PinLockSection(
                        isLocked = pinPending,
                        onSetPin = { onEvent(ProfileFormEvent.PinSetClicked) },
                        onChangePin = { onEvent(ProfileFormEvent.PinChangeClicked) },
                        onRemovePin = { onEvent(ProfileFormEvent.PinRemoveClicked) },
                        note = if (pinPending) stringResource(R.string.pin_lock_applied_when_saved) else null,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(NexusTheme.spacing.lg),
            )
        }
    }
}

@Preview(name = "Profile Setup - PIN Off Light", showBackground = true)
@Preview(name = "Profile Setup - PIN Off Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ProfileSetupScreenPinOffPreview() {
    NexusTheme {
        ProfileSetupContent(
            state = ProfileFormState(name = "Me", colorIndex = 0, emoji = null, pinPending = false),
            pinPending = false,
            onEvent = {},
        )
    }
}

@Preview(name = "Profile Setup - PIN On Light", showBackground = true)
@Preview(name = "Profile Setup - PIN On Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ProfileSetupScreenPinOnPreview() {
    NexusTheme {
        ProfileSetupContent(
            state = ProfileFormState(name = "Me", colorIndex = 0, emoji = null, pinPending = true),
            pinPending = true,
            onEvent = {},
        )
    }
}
