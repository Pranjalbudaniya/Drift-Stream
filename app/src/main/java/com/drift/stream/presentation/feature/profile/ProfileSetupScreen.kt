package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
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

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SetupEffect.Done -> onDone(effect.ownerId)
            }
        }
    }

    ProfileSetupContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
fun ProfileSetupContent(
    state: ProfileFormState,
    onEvent: (ProfileFormEvent) -> Unit,
    modifier: Modifier = Modifier,
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

@Preview(name = "Profile Setup - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun ProfileSetupScreenDarkPreview() {
    NexusTheme {
        ProfileSetupContent(
            state = ProfileFormState(name = "Me", colorIndex = 4, emoji = "🦊"),
            onEvent = {},
        )
    }
}
