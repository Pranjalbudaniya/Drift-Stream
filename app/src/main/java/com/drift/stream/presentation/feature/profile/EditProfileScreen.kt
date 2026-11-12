package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.domain.profile.DeleteHost
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.common.NexusOutlinedButton
import com.drift.stream.presentation.common.NexusTextButton
import com.drift.stream.presentation.theme.NexusTheme

private val DELETE_PROGRESS_SIZE = 24.dp
private val DELETE_PROGRESS_STROKE = 2.dp
private val DELETE_BUTTON_BORDER = 1.dp

@Composable
fun EditProfileScreen(
    profileId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    host: DeleteHost = DeleteHost.Picker,
    viewModel: EditProfileViewModel = hiltViewModel(key = "edit-$profileId"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isEmojiPickerOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.onEvent(EditProfileEvent.PhotoSelected(uri))
                }
            },
        )

    BackHandler(onBack = onBack)

    LaunchedEffect(profileId, host) {
        viewModel.initialize(profileId, host)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditProfileEffect.Done -> onDone()
                is EditProfileEffect.Deleted -> onDelete()
                EditProfileEffect.Close -> onBack()
            }
        }
    }

    LaunchedEffect(state.deleteErrorRes) {
        val res = state.deleteErrorRes
        if (res != null) {
            Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
            viewModel.onEvent(EditProfileEvent.DeleteErrorDismissed)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        EditProfileContent(
            state = state,
            onFormEvent = viewModel::onEvent,
            onEditEvent = viewModel::onEvent,
            onBack = onBack,
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

        val activeBitmap = state.cropBitmap
        if (activeBitmap != null) {
            PhotoCropScreen(
                bitmap = activeBitmap,
                onDone = { cropState, windowPx ->
                    viewModel.onEvent(EditProfileEvent.PhotoCropCompleted(cropState, windowPx))
                },
                onCancel = { viewModel.onEvent(EditProfileEvent.PhotoCropCancelled) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.showDeleteDialog && state.profile != null) {
            DeleteProfileConfirmDialog(
                profileName = state.profile?.name.orEmpty(),
                isDeleting = state.isDeleting,
                onConfirm = { viewModel.onEvent(EditProfileEvent.DeleteConfirmed) },
                onDismiss = { viewModel.onEvent(EditProfileEvent.DeleteCancelled) },
            )
        }
    }
}

@Composable
fun EditProfileContent(
    state: EditProfileUiState,
    onFormEvent: (ProfileFormEvent) -> Unit,
    onEditEvent: (EditProfileEvent) -> Unit,
    onBack: () -> Unit,
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
            val scrollState = rememberScrollState()

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = NexusTheme.spacing.lg)
                        .padding(bottom = NexusTheme.spacing.xl),
            ) {
                ProfileForm(
                    state = state.formState,
                    onEvent = onFormEvent,
                    title = stringResource(R.string.edit_profile_title),
                    subtitle = null,
                    actionButtonText = stringResource(R.string.edit_profile_save_button),
                    onPickPhoto = onPickPhoto,
                    onPickEmoji = onPickEmoji,
                    navigationIcon = {
                        NexusIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.add_profile_back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )

                if (state.canDelete) {
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.md))

                    NexusOutlinedButton(
                        onClick = { onEditEvent(EditProfileEvent.DeleteClicked) },
                        enabled = !state.formState.saving && !state.isDeleting,
                        border = BorderStroke(DELETE_BUTTON_BORDER, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.profile_delete_button),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteProfileConfirmDialog(
    profileName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.profile_delete_dialog_title, profileName),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.profile_delete_dialog_text, profileName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isDeleting) {
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(DELETE_PROGRESS_SIZE),
                            strokeWidth = DELETE_PROGRESS_STROKE,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            NexusTextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
            ) {
                Text(
                    text = stringResource(R.string.profile_delete_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            NexusTextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
            ) {
                Text(stringResource(R.string.profile_delete_dialog_cancel))
            }
        },
    )
}

@Preview(name = "Edit Profile - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun EditProfileScreenDarkPreview() {
    NexusTheme {
        EditProfileContent(
            state =
                EditProfileUiState(
                    formState = ProfileFormState(name = "Asha", colorIndex = 4, emoji = "🦊"),
                    canDelete = true,
                ),
            onFormEvent = {},
            onEditEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Edit Profile - Light", showBackground = true)
@Composable
internal fun EditProfileScreenLightPreview() {
    NexusTheme {
        EditProfileContent(
            state =
                EditProfileUiState(
                    formState = ProfileFormState(name = "Dev", colorIndex = 1, emoji = null),
                    canDelete = false,
                ),
            onFormEvent = {},
            onEditEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Delete Dialog - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DeleteProfileDialogDarkPreview() {
    NexusTheme {
        DeleteProfileConfirmDialog(
            profileName = "Dev",
            isDeleting = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Delete Dialog - Light", showBackground = true)
@Composable
internal fun DeleteProfileDialogLightPreview() {
    NexusTheme {
        DeleteProfileConfirmDialog(
            profileName = "Dev",
            isDeleting = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
