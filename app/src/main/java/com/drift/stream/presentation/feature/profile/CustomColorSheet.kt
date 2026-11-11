package com.drift.stream.presentation.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.profile.argbToHsv
import com.drift.stream.domain.profile.hsvToArgb
import com.drift.stream.presentation.common.NexusButton
import com.drift.stream.presentation.common.NexusTextButton
import com.drift.stream.presentation.common.ProfileAvatar
import com.drift.stream.presentation.theme.NexusTheme

private val PREVIEW_SIZE = 72.dp
private const val HUE_MAX = 360f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorSheet(
    initialColor: Int,
    name: String,
    emoji: String?,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialHsv = remember(initialColor) { argbToHsv(initialColor) }

    var hue by remember { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var value by remember { mutableFloatStateOf(initialHsv.value) }

    val currentArgb =
        remember(hue, saturation, value) {
            hsvToArgb(hue, saturation, value)
        }

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
                    .padding(horizontal = NexusTheme.spacing.lg)
                    .padding(bottom = NexusTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.avatar_custom_color),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))

            ProfileAvatar(
                colorIndex = 0,
                emoji = emoji,
                name = name,
                size = PREVIEW_SIZE,
                customColor = currentArgb,
            )

            Spacer(modifier = Modifier.height(NexusTheme.spacing.lg))

            ColorSliderRow(
                label = stringResource(R.string.avatar_custom_color_hue),
                value = hue,
                valueRange = 0f..HUE_MAX,
                onValueChange = { hue = it },
            )

            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

            ColorSliderRow(
                label = stringResource(R.string.avatar_custom_color_saturation),
                value = saturation,
                valueRange = 0f..1f,
                onValueChange = { saturation = it },
            )

            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

            ColorSliderRow(
                label = stringResource(R.string.avatar_custom_color_brightness),
                value = value,
                valueRange = 0f..1f,
                onValueChange = { value = it },
            )

            Spacer(modifier = Modifier.height(NexusTheme.spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexusTextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.avatar_action_cancel))
                }
                Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))
                NexusButton(
                    onClick = {
                        onApply(currentArgb)
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.avatar_action_done))
                }
            }
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
internal fun CustomColorSheetPreview() {
    NexusTheme {
        CustomColorSheet(
            initialColor = 0xFF2E7D32.toInt(),
            name = "Preview",
            emoji = "🦊",
            onApply = {},
            onDismiss = {},
        )
    }
}
