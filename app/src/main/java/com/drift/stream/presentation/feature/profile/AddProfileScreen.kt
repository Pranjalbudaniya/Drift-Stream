package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.theme.NexusTheme

@Composable
fun AddProfileScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    formKey: Int = 0,
    viewModel: AddProfileViewModel = hiltViewModel(key = "add_profile_$formKey"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val croppingBitmap by viewModel.croppingBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.onPhotoPicked(uri)
                }
            },
        )

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddProfileEffect.Done -> {
                    if (effect.photoSaveFailed) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.profile_add_photo_save_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                    onDone()
                }
            }
        }
    }

    val activeBitmap = croppingBitmap
    if (activeBitmap != null) {
        PhotoCropScreen(
            bitmap = activeBitmap,
            onDone = viewModel::onCropDone,
            onCancel = viewModel::onCropCancel,
            modifier = modifier,
        )
    } else {
        AddProfileContent(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = modifier,
        )
    }
}

@Composable
fun AddProfileContent(
    state: ProfileFormState,
    onEvent: (ProfileFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPickPhoto: (() -> Unit)? = null,
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
                title = stringResource(R.string.add_profile_title),
                subtitle = null,
                actionButtonText = stringResource(R.string.add_profile_button),
                onPickPhoto = onPickPhoto,
                navigationIcon = {
                    NexusIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_profile_back),
                        )
                    }
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

@Preview(name = "Add Profile - Light", showBackground = true)
@Composable
internal fun AddProfileScreenLightPreview() {
    NexusTheme {
        AddProfileContent(
            state = ProfileFormState(name = "", colorIndex = 0, emoji = null),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Add Profile - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun AddProfileScreenDarkPreview() {
    NexusTheme {
        AddProfileContent(
            state = ProfileFormState(name = "", colorIndex = 0, emoji = null),
            onEvent = {},
            onBack = {},
        )
    }
}
