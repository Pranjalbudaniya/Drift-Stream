package com.drift.stream.presentation.feature.backup

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.drift.stream.domain.backup.RejectReason
import com.drift.stream.presentation.theme.NexusTheme

@Preview(name = "Choose - Light", showBackground = true)
@Preview(name = "Choose - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportChoosePreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Choose),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Parsing - Light", showBackground = true)
@Preview(name = "Parsing - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportParsingPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Parsing),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Rejected Not A Backup - Light", showBackground = true)
@Preview(name = "Rejected Not A Backup - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportRejectedNotABackupPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Rejected(RejectReason.NotABackup)),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Rejected Too Large - Light", showBackground = true)
@Preview(name = "Rejected Too Large - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportRejectedTooLargePreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Rejected(RejectReason.TooLarge)),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Rejected Newer Version - Light", showBackground = true)
@Preview(name = "Rejected Newer Version - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportRejectedNewerVersionPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Rejected(RejectReason.NewerVersion)),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Rejected Corrupt - Light", showBackground = true)
@Preview(name = "Rejected Corrupt - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportRejectedCorruptPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Rejected(RejectReason.Corrupt)),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Rejected No Profiles - Light", showBackground = true)
@Preview(name = "Rejected No Profiles - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportRejectedNoProfilesPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Rejected(RejectReason.NoProfiles)),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Rejected Too Many Items - Light", showBackground = true)
@Preview(name = "Rejected Too Many Items - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportRejectedTooManyItemsPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Rejected(RejectReason.TooManyItems)),
            onEvent = {},
            onBack = {},
        )
    }
}
