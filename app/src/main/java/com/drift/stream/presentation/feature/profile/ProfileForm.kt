package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.data.profile.avatarPhotoFile
import com.drift.stream.domain.profile.PROFILE_NAME_MAX_LENGTH
import com.drift.stream.domain.profile.normalizeAvatarColor
import com.drift.stream.presentation.common.NexusButton
import com.drift.stream.presentation.common.ProfileAvatar
import com.drift.stream.presentation.theme.NexusTheme
import com.drift.stream.presentation.theme.tokens.AvatarContent
import com.drift.stream.presentation.theme.tokens.AvatarPalette
import com.drift.stream.presentation.theme.tokens.AvatarPreviewSize
import com.drift.stream.presentation.theme.tokens.AvatarSwatchCircleSize
import com.drift.stream.presentation.theme.tokens.avatarColorNameRes

private val BADGE_SIZE = 32.dp
private val BADGE_ICON_SIZE = 18.dp
private val BADGE_BORDER_WIDTH = 2.dp
private val SWATCH_MIN_TOUCH_SIZE = 48.dp
private val SWATCH_SELECTED_BORDER_WIDTH = 3.dp
private val SWATCH_UNSELECTED_BORDER_WIDTH = 1.dp
private val CHECK_ICON_SIZE = 20.dp
private const val MIN_GRID_COLUMNS = 4
private const val MAX_GRID_COLUMNS = 7

@Composable
fun ProfileForm(
    state: ProfileFormState,
    onEvent: (ProfileFormEvent) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionButtonText: String = stringResource(R.string.profile_form_continue),
    navigationIcon: (@Composable () -> Unit)? = null,
    onPickPhoto: (() -> Unit)? = null,
    onPickEmoji: (() -> Unit)? = null,
    pinLockContent: (@Composable () -> Unit)? = null,
) {
    var isAvatarSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isColorSheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (navigationIcon != null) {
            Box(modifier = Modifier.padding(bottom = NexusTheme.spacing.sm)) {
                navigationIcon()
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(NexusTheme.spacing.xl))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AvatarPreviewWithBadge(
                state = state,
                onOpenAvatarEditor = { isAvatarSheetOpen = true },
            )
        }

        if (state.photoError) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            Text(
                text = stringResource(R.string.photo_open_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(NexusTheme.spacing.xl))

        ProfileNameField(
            name = state.name,
            nameError = state.nameError,
            enabled = !state.saving,
            onNameChanged = { onEvent(ProfileFormEvent.NameChanged(it)) },
            onSubmit = { onEvent(ProfileFormEvent.Submit) },
        )

        if (state.avatarKind != AvatarKind.Photo) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.lg))

            Text(
                text = stringResource(R.string.profile_form_color_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

            AvatarColorGrid(
                selectedColorIndex = state.colorIndex,
                customColor = state.customColor,
                enabled = !state.saving,
                onColorPicked = { onEvent(ProfileFormEvent.ColorPicked(it)) },
                onCustomColorClick = { isColorSheetOpen = true },
            )
        }

        if (pinLockContent != null) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.lg))
            pinLockContent()
        }

        Spacer(modifier = Modifier.height(NexusTheme.spacing.xl))

        NexusButton(
            onClick = { onEvent(ProfileFormEvent.Submit) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(CHECK_ICON_SIZE),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(text = actionButtonText)
            }
        }

        if (state.saveFailed) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            Text(
                text = stringResource(R.string.profile_error_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (isAvatarSheetOpen) {
        val hasCustom = state.emoji != null || state.avatarKind == AvatarKind.Photo
        AvatarEditorSheet(
            hasCustomAvatar = hasCustom,
            onPickEmoji = { onPickEmoji?.invoke() },
            onPickPhoto = onPickPhoto,
            onUseInitial = { onEvent(ProfileFormEvent.AvatarCleared) },
            onDismiss = { isAvatarSheetOpen = false },
        )
    }

    if (isColorSheetOpen) {
        val initialArgb = state.customColor ?: AvatarPalette[normalizeAvatarColor(state.colorIndex)].hashCode()
        CustomColorSheet(
            initialColor = initialArgb,
            name = state.name,
            emoji = state.emoji,
            onApply = { onEvent(ProfileFormEvent.CustomColorPicked(it)) },
            onDismiss = { isColorSheetOpen = false },
        )
    }
}

@Composable
private fun AvatarPreviewWithBadge(
    state: ProfileFormState,
    onOpenAvatarEditor: () -> Unit,
) {
    val previewDesc = stringResource(R.string.avatar_preview_content_description)
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoModel =
        if (state.avatarKind == AvatarKind.Photo) {
            state.pendingPhotoPath ?: state.existingPhotoProfileId?.let { id ->
                avatarPhotoFile(context.filesDir, id)
            }
        } else {
            null
        }
    val memoryCacheKey =
        if (state.avatarKind == AvatarKind.Photo && state.pendingPhotoPath == null &&
            state.existingPhotoProfileId != null
        ) {
            "avatar:${state.existingPhotoProfileId}:${state.existingPhotoStamp}"
        } else {
            null
        }

    Box(
        modifier = Modifier.size(AvatarPreviewSize),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(
            colorIndex = state.colorIndex,
            emoji = state.emoji,
            name = state.name,
            size = AvatarPreviewSize,
            customColor = state.customColor,
            photoModel = photoModel,
            memoryCacheKey = memoryCacheKey,
            modifier = Modifier.semantics { this.contentDescription = previewDesc },
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .size(BADGE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        BorderStroke(BADGE_BORDER_WIDTH, MaterialTheme.colorScheme.surface),
                        CircleShape,
                    ).clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.avatar_change_badge),
                        onClick = onOpenAvatarEditor,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.avatar_change_badge),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(BADGE_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun AvatarColorGrid(
    selectedColorIndex: Int,
    customColor: Int?,
    enabled: Boolean,
    onColorPicked: (Int) -> Unit,
    onCustomColorClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val totalWidth = maxWidth
        val columns = (totalWidth / SWATCH_MIN_TOUCH_SIZE).toInt().coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        val cellWidth = totalWidth / columns

        val normalizedSelected = normalizeAvatarColor(selectedColorIndex)
        val isCustomActive = customColor != null

        val totalCells = AvatarPalette.size + 1
        val rowCount = (totalCells + columns - 1) / columns

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        ) {
            for (rowIndex in 0 until rowCount) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (colIndex in 0 until columns) {
                        val itemIndex = rowIndex * columns + colIndex
                        if (itemIndex < AvatarPalette.size) {
                            val isSelected = !isCustomActive && itemIndex == normalizedSelected
                            val color = AvatarPalette[itemIndex]
                            val colorName = stringResource(avatarColorNameRes(itemIndex))
                            val contentDesc =
                                if (isSelected) {
                                    stringResource(R.string.avatar_color_swatch_selected, colorName)
                                } else {
                                    colorName
                                }

                            Box(
                                modifier = Modifier.width(cellWidth),
                                contentAlignment = Alignment.Center,
                            ) {
                                AvatarPaletteSwatchItem(
                                    color = color,
                                    isSelected = isSelected,
                                    contentDescription = contentDesc,
                                    enabled = enabled,
                                    onClick = { onColorPicked(itemIndex) },
                                )
                            }
                        } else if (itemIndex == AvatarPalette.size) {
                            Box(
                                modifier = Modifier.width(cellWidth),
                                contentAlignment = Alignment.Center,
                            ) {
                                AvatarCustomColorSwatchItem(
                                    customColor = customColor,
                                    isSelected = isCustomActive,
                                    enabled = enabled,
                                    onClick = onCustomColorClick,
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(cellWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarPaletteSwatchItem(
    color: Color,
    isSelected: Boolean,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .size(SWATCH_MIN_TOUCH_SIZE)
                .semantics {
                    this.contentDescription = contentDescription
                    this.selected = isSelected
                }.selectable(
                    selected = isSelected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AvatarSwatchCircleSize)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                BorderStroke(SWATCH_SELECTED_BORDER_WIDTH, MaterialTheme.colorScheme.onSurface),
                                CircleShape,
                            )
                        } else {
                            Modifier.border(
                                BorderStroke(SWATCH_UNSELECTED_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                                CircleShape,
                            )
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = AvatarContent,
                    modifier = Modifier.size(CHECK_ICON_SIZE),
                )
            }
        }
    }
}

@Composable
private fun AvatarCustomColorSwatchItem(
    customColor: Int?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentDesc =
        if (isSelected) {
            stringResource(R.string.avatar_custom_color_selected)
        } else {
            stringResource(R.string.avatar_custom_color)
        }

    val backgroundColor =
        if (customColor != null) {
            Color(customColor)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .size(SWATCH_MIN_TOUCH_SIZE)
                .semantics {
                    this.contentDescription = contentDesc
                    this.selected = isSelected
                }.selectable(
                    selected = isSelected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AvatarSwatchCircleSize)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                BorderStroke(SWATCH_SELECTED_BORDER_WIDTH, MaterialTheme.colorScheme.onSurface),
                                CircleShape,
                            )
                        } else {
                            Modifier.border(
                                BorderStroke(SWATCH_UNSELECTED_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                                CircleShape,
                            )
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = AvatarContent,
                    modifier = Modifier.size(CHECK_ICON_SIZE),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(CHECK_ICON_SIZE),
                )
            }
        }
    }
}

@Composable
private fun ProfileNameField(
    name: String,
    nameError: NameError?,
    enabled: Boolean,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val charCount = name.codePointCount(0, name.length)
    val errorRes =
        when (nameError) {
            NameError.Empty -> R.string.profile_error_name_empty
            NameError.TooLong -> R.string.profile_error_name_too_long
            NameError.Invalid -> R.string.profile_error_name_invalid
            NameError.Duplicate -> R.string.profile_error_name_duplicate
            null -> null
        }

    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.profile_form_name_label)) },
        singleLine = true,
        enabled = enabled,
        isError = nameError != null,
        shape = NexusTheme.shapes.card,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        supportingText = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(
                    text = "$charCount/$PROFILE_NAME_MAX_LENGTH",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (nameError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Light")
@Composable
internal fun ProfileFormLightPreview() {
    NexusTheme {
        ProfileForm(
            state = ProfileFormState(name = "Alex", colorIndex = 2, emoji = "🦊"),
            onEvent = {},
            title = stringResource(R.string.profile_setup_title),
        )
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ProfileFormDarkPreview() {
    NexusTheme {
        ProfileForm(
            state = ProfileFormState(name = "Alex", colorIndex = 2, emoji = null),
            onEvent = {},
            title = stringResource(R.string.add_profile_title),
        )
    }
}
