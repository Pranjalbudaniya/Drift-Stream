package com.drift.stream.presentation.feature.backup

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.drift.stream.domain.backup.BackupPreview
import com.drift.stream.domain.backup.ImportSummary
import com.drift.stream.domain.backup.ImportWarning
import com.drift.stream.domain.backup.PreviewProfile
import com.drift.stream.presentation.theme.NexusTheme

private val SAMPLE_PREVIEW =
    BackupPreview(
        createdAt = 1700000000000L,
        appVersion = "1.2.0",
        profiles =
            listOf(
                PreviewProfile(
                    index = 0,
                    name = "Asha",
                    libraryCount = 120,
                    progressCount = 842,
                    searchCount = 34,
                    addonCount = 2,
                    hasPhoto = false,
                ),
            ),
    )

private val SAMPLE_SUMMARY =
    ImportSummary(
        profilesCreated = 1,
        libraryItems = 120,
        progressEntries = 842,
        searches = 34,
        addons = 2,
        skipped = 3,
        warnings = listOf(ImportWarning.PhotoNotSaved, ImportWarning.SettingsNotFullySaved),
    )

@Preview(name = "Preview AddAsNew - Light", showBackground = true)
@Preview(name = "Preview AddAsNew - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportPreviewAddAsNewPreview() {
    NexusTheme {
        ImportDataContent(
            state =
                ImportDataUiState(
                    isAddAsNew = true,
                    status = ImportStatus.Preview,
                    preview = SAMPLE_PREVIEW,
                    selectedProfileIndexes = setOf(0),
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Preview Into - Light", showBackground = true)
@Preview(name = "Preview Into - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportPreviewIntoPreview() {
    NexusTheme {
        ImportDataContent(
            state =
                ImportDataUiState(
                    isAddAsNew = false,
                    targetProfileName = "Target",
                    status = ImportStatus.Preview,
                    preview = SAMPLE_PREVIEW,
                    intoMode = SelectedMode.Merge,
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Replace Dialog - Light", showBackground = true)
@Preview(name = "Replace Dialog - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportReplaceDialogPreview() {
    NexusTheme {
        ImportDataContent(
            state =
                ImportDataUiState(
                    isAddAsNew = false,
                    targetProfileName = "Target",
                    status = ImportStatus.ConfirmReplace,
                    preview = SAMPLE_PREVIEW,
                    intoMode = SelectedMode.Replace,
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Importing - Light", showBackground = true)
@Preview(name = "Importing - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportImportingPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Importing),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Summary With Warnings - Light", showBackground = true)
@Preview(name = "Summary With Warnings - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportSummaryWithWarningsPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Summary(SAMPLE_SUMMARY)),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Error - Light", showBackground = true)
@Preview(name = "Error - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ImportErrorPreview() {
    NexusTheme {
        ImportDataContent(
            state = ImportDataUiState(status = ImportStatus.Error),
            onEvent = {},
            onBack = {},
        )
    }
}
