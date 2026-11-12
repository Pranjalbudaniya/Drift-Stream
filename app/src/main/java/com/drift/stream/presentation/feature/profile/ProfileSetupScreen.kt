package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                is SetupEffect.Done -> onDone(effect.ownerId)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ProfileSetupContent(
            state = state,
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(NexusTheme.spacing.lg),
            )
        }
    }
}

@Preview(name = "Profile Setup - Light", showBackground = true)
@Composable
internal fun ProfileSetupScreenLightPreview() {
    NexusTheme {
        ProfileSetupContent(
            state = ProfileFormState(name = "Me", colorIndex = 0, emoji = null),
            onEvent = {},
        )
    }
}

@Preview(name = "Profile Setup - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ProfileSetupScreenDarkPreview() {
    NexusTheme {
        ProfileSetupContent(
            state = ProfileFormState(name = "Me", colorIndex = 0, emoji = null),
            onEvent = {},
        )
    }
}
