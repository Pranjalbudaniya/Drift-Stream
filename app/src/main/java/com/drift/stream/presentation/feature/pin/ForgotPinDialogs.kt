package com.drift.stream.presentation.feature.pin

import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.drift.stream.R
import com.drift.stream.presentation.common.NexusTextButton
import com.drift.stream.presentation.theme.NexusTheme

@Composable
fun OwnerEraseDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.pin_forgot_owner_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.pin_forgot_owner_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            NexusTextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.pin_forgot_owner_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.profile_delete_dialog_cancel))
            }
        },
        modifier = modifier,
    )
}

@Composable
fun OthersInfoDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(R.string.pin_forgot_others_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_ok))
            }
        },
        modifier = modifier,
    )
}

@Composable
fun EraseFailedDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(R.string.pin_forgot_erase_failed),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_ok))
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ForgotPinHost(
    forgot: ForgotState?,
    onEvent: (PinPadEvent) -> Unit,
) {
    when (forgot) {
        ForgotState.OwnerErase -> {
            OwnerEraseDialog(
                onConfirm = { onEvent(PinPadEvent.EraseConfirmed) },
                onDismiss = { onEvent(PinPadEvent.EraseDismissed) },
            )
        }

        ForgotState.OthersInfo -> {
            OthersInfoDialog(
                onDismiss = { onEvent(PinPadEvent.ForgotInfoDismissed) },
            )
        }

        ForgotState.EraseFailed -> {
            EraseFailedDialog(
                onDismiss = { onEvent(PinPadEvent.ForgotInfoDismissed) },
            )
        }

        null -> {}
    }
}

@Preview(name = "Owner Erase Dialog - Light", showBackground = true)
@Preview(name = "Owner Erase Dialog - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun OwnerEraseDialogPreview() {
    NexusTheme {
        OwnerEraseDialog(
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Others Info Dialog - Light", showBackground = true)
@Preview(name = "Others Info Dialog - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun OthersInfoDialogPreview() {
    NexusTheme {
        OthersInfoDialog(
            onDismiss = {},
        )
    }
}
