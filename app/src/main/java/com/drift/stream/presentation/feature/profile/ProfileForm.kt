package com.drift.stream.presentation.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.profile.PROFILE_NAME_MAX_LENGTH
import com.drift.stream.domain.profile.normalizeAvatarColor
import com.drift.stream.presentation.common.NexusButton
import com.drift.stream.presentation.common.ProfileAvatar
import com.drift.stream.presentation.theme.NexusTheme
import com.drift.stream.presentation.theme.tokens.AvatarContent
import com.drift.stream.presentation.theme.tokens.AvatarEmojis
import com.drift.stream.presentation.theme.tokens.AvatarPalette
import com.drift.stream.presentation.theme.tokens.AvatarPreviewSize
import com.drift.stream.presentation.theme.tokens.AvatarSwatchCircleSize
import com.drift.stream.presentation.theme.tokens.AvatarTouchTargetSize
import com.drift.stream.presentation.theme.tokens.avatarColorNameRes

@Composable
fun ProfileForm(
    state: ProfileFormState,
    onEvent: (ProfileFormEvent) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionButtonText: String = stringResource(R.string.profile_form_continue),
    navigationIcon: (@Composable () -> Unit)? = null,
) {
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
            val previewDesc = stringResource(R.string.avatar_preview_content_description)
            ProfileAvatar(
                colorIndex = state.colorIndex,
                emoji = state.emoji,
                name = state.name,
                size = AvatarPreviewSize,
                modifier = Modifier.semantics { this.contentDescription = previewDesc },
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

        Spacer(modifier = Modifier.height(NexusTheme.spacing.lg))

        Text(
            text = stringResource(R.string.profile_form_color_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

        AvatarColorSwatches(
            selectedColorIndex = state.colorIndex,
            enabled = !state.saving,
            onColorPicked = { onEvent(ProfileFormEvent.ColorPicked(it)) },
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.lg))

        Text(
            text = stringResource(R.string.profile_form_emoji_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

        AvatarEmojiGrid(
            selectedEmoji = state.emoji,
            enabled = !state.saving,
            onEmojiPicked = { onEvent(ProfileFormEvent.EmojiPicked(it)) },
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.xl))

        NexusButton(
            onClick = { onEvent(ProfileFormEvent.Submit) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarColorSwatches(
    selectedColorIndex: Int,
    enabled: Boolean,
    onColorPicked: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val normalizedSelected = normalizeAvatarColor(selectedColorIndex)
        AvatarPalette.forEachIndexed { index, color ->
            val isSelected = index == normalizedSelected
            val colorName = stringResource(avatarColorNameRes(index))
            val contentDesc =
                if (isSelected) {
                    stringResource(R.string.avatar_color_swatch_selected, colorName)
                } else {
                    colorName
                }

            AvatarSwatchItem(
                color = color,
                isSelected = isSelected,
                contentDescription = contentDesc,
                enabled = enabled,
                onClick = { onColorPicked(index) },
            )
        }
    }
}

@Composable
private fun AvatarSwatchItem(
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
                .size(AvatarTouchTargetSize)
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
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                                CircleShape,
                            )
                        } else {
                            Modifier.border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarEmojiGrid(
    selectedEmoji: String?,
    enabled: Boolean,
    onEmojiPicked: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val useInitialDesc = stringResource(R.string.profile_form_use_initial)
        val initialSelected = selectedEmoji == null

        Box(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(AvatarTouchTargetSize)
                    .semantics {
                        this.contentDescription = useInitialDesc
                        this.selected = initialSelected
                    }.selectable(
                        selected = initialSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onEmojiPicked(null) },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(AvatarSwatchCircleSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (initialSelected) {
                                Modifier.border(
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                    CircleShape,
                                )
                            } else {
                                Modifier.border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    CircleShape,
                                )
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AvatarEmojis.forEach { emoji ->
            val isSelected = selectedEmoji == emoji
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .size(AvatarTouchTargetSize)
                        .semantics {
                            this.contentDescription = emoji
                            this.selected = isSelected
                        }.selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onEmojiPicked(emoji) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(AvatarSwatchCircleSize)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                            ).then(
                                if (isSelected) {
                                    Modifier.border(
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
