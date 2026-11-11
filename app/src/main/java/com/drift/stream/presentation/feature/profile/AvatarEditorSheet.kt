package com.drift.stream.presentation.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.presentation.theme.NexusTheme

private val MIN_TOUCH_TARGET = 48.dp
private val ROW_VERTICAL_PADDING = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarEditorSheet(
    hasCustomAvatar: Boolean,
    onPickEmoji: () -> Unit,
    onPickPhoto: (() -> Unit)?,
    onUseInitial: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = NexusTheme.shapes.card,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = NexusTheme.spacing.lg),
        ) {
            AvatarSheetOption(
                icon = Icons.Default.Face,
                text = stringResource(R.string.avatar_choice_emoji),
                onClick = {
                    onDismiss()
                    onPickEmoji()
                },
            )

            if (onPickPhoto != null) {
                AvatarSheetOption(
                    icon = Icons.Default.AccountCircle,
                    text = stringResource(R.string.avatar_choice_photo),
                    onClick = {
                        onDismiss()
                        onPickPhoto()
                    },
                )
            }

            if (hasCustomAvatar) {
                AvatarSheetOption(
                    icon = Icons.Default.Clear,
                    text = stringResource(R.string.avatar_choice_initial),
                    onClick = {
                        onDismiss()
                        onUseInitial()
                    },
                )
            }
        }
    }
}

@Composable
private fun AvatarSheetOption(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = NexusTheme.spacing.lg,
                    vertical = ROW_VERTICAL_PADDING,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(NexusTheme.spacing.lg),
        )
        Spacer(modifier = Modifier.width(NexusTheme.spacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview
@Composable
internal fun AvatarEditorSheetPreview() {
    NexusTheme {
        AvatarEditorSheet(
            hasCustomAvatar = true,
            onPickEmoji = {},
            onPickPhoto = {},
            onUseInitial = {},
            onDismiss = {},
        )
    }
}
