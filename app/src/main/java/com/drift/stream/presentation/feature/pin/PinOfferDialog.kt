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

enum class PinOfferState {
    None,
    Asking,
}

sealed interface PinOfferEvent {
    data object SetPin : PinOfferEvent

    data object Skip : PinOfferEvent

    data object Dismissed : PinOfferEvent

    companion object {
        val PinOfferSetPin: PinOfferEvent = SetPin
        val PinOfferSkip: PinOfferEvent = Skip
        val PinOfferDismissed: PinOfferEvent = Dismissed
    }
}

val PinOfferSetPin: PinOfferEvent = PinOfferEvent.SetPin
val PinOfferSkip: PinOfferEvent = PinOfferEvent.Skip
val PinOfferDismissed: PinOfferEvent = PinOfferEvent.Dismissed

@Composable
fun PinOfferDialog(
    onSetPin: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.pin_offer_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.pin_offer_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            NexusTextButton(onClick = onSetPin) {
                Text(text = stringResource(R.string.pin_set_button))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onSkip) {
                Text(text = stringResource(R.string.pin_offer_skip))
            }
        },
        modifier = modifier,
    )
}

@Preview(name = "PIN Offer Dialog - Light", showBackground = true)
@Preview(name = "PIN Offer Dialog - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun PinOfferDialogPreview() {
    NexusTheme {
        PinOfferDialog(
            onSetPin = {},
            onSkip = {},
            onDismiss = {},
        )
    }
}
