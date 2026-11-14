package com.drift.stream.presentation.feature.pin

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.PIN_LENGTH
import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.presentation.common.NexusTextButton
import com.drift.stream.presentation.common.ProfileAvatarFor
import com.drift.stream.presentation.theme.NexusTheme
import java.util.Locale

val PIN_KEY_SIZE = 72.dp
val PIN_AVATAR_SIZE = 64.dp
val PIN_DOT_SIZE = 14.dp
val PIN_DOT_SPACING = 12.dp
val PIN_MESSAGE_HEIGHT = 24.dp
val PIN_ROW_SPACING = 12.dp
val PIN_PROGRESS_SIZE = 16.dp
val PIN_PROGRESS_STROKE = 2.dp
private const val SECONDS_PER_MINUTE = 60
private const val DISABLED_ALPHA = 0.38f
private const val DIGIT_BG_ALPHA_ENABLED = 0.6f
private const val DIGIT_BG_ALPHA_DISABLED = 0.2f
private const val PREVIEW_CONFIRM_DOT_COUNT = 4
private const val TITLE_CROSSFADE_MS = 150
private const val SHAKE_DURATION_MS = 280
private const val SHAKE_AMP_1 = 16f
private const val SHAKE_AMP_2 = 12f
private const val SHAKE_AMP_3 = 8f
private const val SHAKE_AMP_4 = 4f
private const val SHAKE_STEP_1_MS = 35
private const val SHAKE_STEP_2_MS = 70
private const val SHAKE_STEP_3_MS = 105
private const val SHAKE_STEP_4_MS = 140
private const val SHAKE_STEP_5_MS = 175
private const val SHAKE_STEP_6_MS = 210
private const val SHAKE_STEP_7_MS = 245

@Composable
fun PinPadScreen(
    pendingPin: PendingPin,
    modifier: Modifier = Modifier,
    viewModel: PinPadViewModel = hiltViewModel(key = "pin-${pendingPin.id}"),
) {
    LaunchedEffect(pendingPin.id) {
        viewModel.initialize(pendingPin)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onEvent(PinPadEvent.Cancel)
    }

    PinPadContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
internal fun PinPadContent(
    uiState: PinPadUiState,
    onEvent: (PinPadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val haptic = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(uiState.rejectCount) {
        if (uiState.rejectCount > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec =
                    keyframes {
                        durationMillis = SHAKE_DURATION_MS
                        -SHAKE_AMP_1 at SHAKE_STEP_1_MS using FastOutLinearInEasing
                        SHAKE_AMP_1 at SHAKE_STEP_2_MS using LinearOutSlowInEasing
                        -SHAKE_AMP_2 at SHAKE_STEP_3_MS using FastOutLinearInEasing
                        SHAKE_AMP_2 at SHAKE_STEP_4_MS using LinearOutSlowInEasing
                        -SHAKE_AMP_3 at SHAKE_STEP_5_MS using FastOutLinearInEasing
                        SHAKE_AMP_3 at SHAKE_STEP_6_MS using LinearOutSlowInEasing
                        -SHAKE_AMP_4 at SHAKE_STEP_7_MS using FastOutLinearInEasing
                        0f at SHAKE_DURATION_MS
                    },
            )
        }
    }

    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = { onEvent(PinPadEvent.Cancel) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.pin_cancel_description),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PinHeader(uiState = uiState)

            Spacer(modifier = Modifier.height(spacing.lg))

            val dotsDesc =
                pluralStringResource(
                    R.plurals.pin_digits_entered,
                    uiState.dotCount,
                    uiState.dotCount,
                )
            Row(
                modifier =
                    Modifier
                        .offset(x = shakeOffset.value.dp)
                        .semantics {
                            contentDescription = dotsDesc
                        },
                horizontalArrangement = Arrangement.spacedBy(PIN_DOT_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until PIN_LENGTH) {
                    val filled = i < uiState.dotCount
                    Box(
                        modifier =
                            Modifier
                                .size(PIN_DOT_SIZE)
                                .clip(CircleShape)
                                .then(
                                    if (filled) {
                                        Modifier.background(MaterialTheme.colorScheme.primary)
                                    } else {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        )
                                    },
                                ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            if (uiState.holding) {
                Row(
                    modifier = Modifier.height(PIN_MESSAGE_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PIN_PROGRESS_SIZE),
                        strokeWidth = PIN_PROGRESS_STROKE,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.pin_opening),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PinMessageView(message = uiState.message)
            }

            if (uiState.isVerify && !uiState.holding) {
                NexusTextButton(
                    onClick = { onEvent(PinPadEvent.ForgotPinPressed) },
                ) {
                    Text(
                        text = stringResource(R.string.pin_forgot_button),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(spacing.md))
            }

            PinKeypad(
                uiState = uiState,
                onEvent = onEvent,
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!uiState.isVerify) {
                Text(
                    text = stringResource(R.string.pin_honest_limit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = spacing.md),
                )
            }

            ForgotPinHost(
                forgot = uiState.forgot,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun PinHeader(uiState: PinPadUiState) {
    val spacing = NexusTheme.spacing
    val currentProfile = uiState.profile
    val isOwnerPad =
        uiState.titleKind == PinTitleKind.OwnerResetPin ||
            uiState.titleKind == PinTitleKind.OwnerDeletePin ||
            uiState.titleKind == PinTitleKind.OwnerManagePin
    if ((uiState.titleKind == PinTitleKind.EnterPin || isOwnerPad) && currentProfile != null) {
        ProfileAvatarFor(
            profile = currentProfile,
            size = PIN_AVATAR_SIZE,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = uiState.profileName.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        val subtitleRes =
            when (uiState.titleKind) {
                PinTitleKind.OwnerResetPin -> R.string.pin_enter_owner_reset_title
                PinTitleKind.OwnerDeletePin -> R.string.pin_enter_owner_delete_title
                PinTitleKind.OwnerManagePin -> R.string.pin_enter_owner_manage_title
                else -> R.string.pin_enter_title
            }
        Text(
            text = stringResource(subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    } else {
        Crossfade(
            targetState = uiState.titleKind,
            animationSpec = tween(durationMillis = TITLE_CROSSFADE_MS),
            label = "PinCreateTitleCrossfade",
        ) { kind ->
            Text(
                text =
                    if (kind == PinTitleKind.CreatePin) {
                        stringResource(R.string.pin_create_title)
                    } else {
                        stringResource(R.string.pin_confirm_title)
                    },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = stringResource(R.string.pin_choose_length_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinMessageView(
    message: PinMessage?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(PIN_MESSAGE_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (message != null) {
            val text =
                when (message) {
                    is PinMessage.Wrong -> {
                        pluralStringResource(
                            R.plurals.pin_wrong_tries_left,
                            message.triesLeft,
                            message.triesLeft,
                        )
                    }

                    is PinMessage.Locked -> {
                        val m = message.remainingSeconds / SECONDS_PER_MINUTE
                        val s = message.remainingSeconds % SECONDS_PER_MINUTE
                        val formatted = String.format(Locale.US, "%d:%02d", m, s)
                        stringResource(R.string.pin_locked_countdown, formatted)
                    }

                    is PinMessage.Mismatch -> {
                        stringResource(R.string.pin_mismatch)
                    }
                }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PinKeypad(
    uiState: PinPadUiState,
    onEvent: (PinPadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keysDisabled = uiState.busy || uiState.locked || uiState.holding
    val digitEnabled = !keysDisabled && uiState.dotCount < PIN_LENGTH
    val backspaceEnabled = !keysDisabled && uiState.dotCount > 0
    val confirmEnabled = !keysDisabled && uiState.dotCount == PIN_LENGTH

    val rows =
        listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PIN_ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { rowDigits ->
            Row(horizontalArrangement = Arrangement.spacedBy(PIN_ROW_SPACING)) {
                rowDigits.forEach { digit ->
                    PinKeyButton(
                        enabled = digitEnabled,
                        contentDescription = digit.toString(),
                        hasBackground = true,
                        onClick = { onEvent(PinPadEvent.DigitPressed(digit)) },
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PIN_ROW_SPACING)) {
            PinKeyButton(
                enabled = backspaceEnabled,
                contentDescription = stringResource(R.string.pin_backspace_description),
                onClick = { onEvent(PinPadEvent.Backspace) },
            ) {
                Text(
                    text = "⌫",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            PinKeyButton(
                enabled = digitEnabled,
                contentDescription = '0'.toString(),
                hasBackground = true,
                onClick = { onEvent(PinPadEvent.DigitPressed('0')) },
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (uiState.isVerify) {
                Box(modifier = Modifier.size(PIN_KEY_SIZE))
            } else {
                PinKeyButton(
                    enabled = confirmEnabled,
                    contentDescription = stringResource(R.string.pin_confirm_description),
                    onClick = { onEvent(PinPadEvent.Confirm) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint =
                            if (confirmEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val bgAlpha = if (enabled) DIGIT_BG_ALPHA_ENABLED else DIGIT_BG_ALPHA_DISABLED
    val bgColor =
        if (hasBackground) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha)
        } else {
            Color.Transparent
        }
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
    ) {
        Box(
            modifier =
                modifier
                    .size(PIN_KEY_SIZE)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    ).semantics {
                        role = Role.Button
                        this.contentDescription = contentDescription
                    },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private val samplePreviewProfile =
    Profile(
        id = 1L,
        name = "Asha",
        avatarColor = 2,
        avatarEmoji = "🦊",
        isOwner = false,
        needsSetup = false,
        createdAt = 1000L,
        lastUsedAt = 1000L,
        sortOrder = 0,
    )

@Preview(name = "PinPad - Verify 0 Digits Light", showBackground = true)
@Preview(name = "PinPad - Verify 0 Digits Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun PinPadVerify0DigitsPreview() {
    NexusTheme {
        PinPadContent(
            uiState =
                PinPadUiState(
                    titleKind = PinTitleKind.EnterPin,
                    profile = samplePreviewProfile,
                    profileName = "Asha",
                    dotCount = 0,
                    isVerify = true,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PinPad - Verify 2 Digits Light", showBackground = true)
@Preview(name = "PinPad - Verify 2 Digits Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun PinPadVerify2DigitsPreview() {
    NexusTheme {
        PinPadContent(
            uiState =
                PinPadUiState(
                    titleKind = PinTitleKind.EnterPin,
                    profile = samplePreviewProfile,
                    profileName = "Asha",
                    dotCount = 2,
                    isVerify = true,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PinPad - Create First Light", showBackground = true)
@Preview(name = "PinPad - Create First Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun PinPadCreateFirstPreview() {
    NexusTheme {
        PinPadContent(
            uiState =
                PinPadUiState(
                    titleKind = PinTitleKind.CreatePin,
                    dotCount = 0,
                    isVerify = false,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PinPad - Create Confirm Light", showBackground = true)
@Preview(name = "PinPad - Create Confirm Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun PinPadCreateConfirmPreview() {
    NexusTheme {
        PinPadContent(
            uiState =
                PinPadUiState(
                    titleKind = PinTitleKind.ConfirmPin,
                    dotCount = PREVIEW_CONFIRM_DOT_COUNT,
                    isVerify = false,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PinPad - Opening Light", showBackground = true)
@Preview(name = "PinPad - Opening Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun PinPadOpeningPreview() {
    NexusTheme {
        PinPadContent(
            uiState =
                PinPadUiState(
                    titleKind = PinTitleKind.EnterPin,
                    profile = samplePreviewProfile,
                    profileName = "Asha",
                    dotCount = 0,
                    isVerify = true,
                    holding = true,
                ),
            onEvent = {},
        )
    }
}
