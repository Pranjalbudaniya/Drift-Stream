package com.drift.stream.presentation.feature.backup

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.drift.stream.domain.backup.ExportScope
import com.drift.stream.domain.backup.ExportSummary
import com.drift.stream.presentation.theme.NexusTheme

private const val PREVIEW_PROFILES = 1
private const val PREVIEW_LIBRARY = 10
private const val PREVIEW_PROGRESS = 5
private const val PREVIEW_SEARCHES = 3
private const val PREVIEW_ADDONS = 2

@Preview(name = "Options - Light", showBackground = true)
@Composable
internal fun ExportOptionsLightPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(scope = ExportScope.All),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Options - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ExportOptionsDarkPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(scope = ExportScope.All),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Options Warning - Light", showBackground = true)
@Composable
internal fun ExportOptionsWarningLightPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(scope = ExportScope.Profile(1L), profileName = "Alice", includeAddons = true),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Options Warning - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ExportOptionsWarningDarkPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(scope = ExportScope.Profile(1L), profileName = "Alice", includeAddons = true),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Writing - Light", showBackground = true)
@Composable
internal fun ExportWritingLightPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(status = ExportStatus.Writing),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Writing - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ExportWritingDarkPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(status = ExportStatus.Writing),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Done - Light", showBackground = true)
@Composable
internal fun ExportDoneLightPreview() {
    NexusTheme {
        ExportDataContent(
            state =
                ExportDataUiState(
                    status =
                        ExportStatus.Done(
                            ExportSummary(
                                PREVIEW_PROFILES,
                                PREVIEW_LIBRARY,
                                PREVIEW_PROGRESS,
                                PREVIEW_SEARCHES,
                                PREVIEW_ADDONS,
                            ),
                        ),
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Done - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ExportDoneDarkPreview() {
    NexusTheme {
        ExportDataContent(
            state =
                ExportDataUiState(
                    status =
                        ExportStatus.Done(
                            ExportSummary(
                                PREVIEW_PROFILES,
                                PREVIEW_LIBRARY,
                                PREVIEW_PROGRESS,
                                PREVIEW_SEARCHES,
                                PREVIEW_ADDONS,
                            ),
                        ),
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Error - Light", showBackground = true)
@Composable
internal fun ExportErrorLightPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(status = ExportStatus.Error()),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Error - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ExportErrorDarkPreview() {
    NexusTheme {
        ExportDataContent(
            state = ExportDataUiState(status = ExportStatus.Error()),
            onEvent = {},
            onBack = {},
        )
    }
}
