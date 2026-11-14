package com.drift.stream.presentation.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.domain.backup.ExportScope
import com.drift.stream.domain.backup.ExportSummary
import com.drift.stream.presentation.common.NexusButton
import com.drift.stream.presentation.common.NexusCard
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.theme.NexusTheme

private const val JSON_MIME_TYPE = "application/json"
private val PROGRESS_INDICATOR_SIZE = 48.dp

@Composable
fun ExportDataScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(JSON_MIME_TYPE),
            onResult = { uri ->
                viewModel.onEvent(ExportDataEvent.FileChosen(uri))
            },
        )

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportDataEffect.LaunchFilePicker -> {
                    filePickerLauncher.launch(effect.suggestedName)
                }

                is ExportDataEffect.Close -> {
                    onBack()
                }
            }
        }
    }

    ExportDataContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataContent(
    state: ExportDataUiState,
    onEvent: (ExportDataEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val title =
        when (state.scope) {
            is ExportScope.All -> stringResource(R.string.export_all_profiles)
            is ExportScope.Profile -> stringResource(R.string.export_profile_title, state.profileName)
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    NexusIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            when (val status = state.status) {
                is ExportStatus.Options -> {
                    OptionsContent(state = state, onEvent = onEvent)
                }

                is ExportStatus.Writing -> {
                    WritingContent()
                }

                is ExportStatus.Done -> {
                    DoneContent(summary = status.summary, onDone = { onEvent(ExportDataEvent.DoneClicked) })
                }

                is ExportStatus.Error -> {
                    ErrorContent(
                        isTooLarge = status.isTooLarge,
                        onRetry = { onEvent(ExportDataEvent.Retry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsContent(
    state: ExportDataUiState,
    onEvent: (ExportDataEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
    ) {
        Text(
            text = stringResource(R.string.export_included_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(spacing.sm))

        BulletItem(stringResource(R.string.export_included_watchlist_library))
        BulletItem(stringResource(R.string.export_included_watch_history_progress))
        BulletItem(stringResource(R.string.export_included_settings))

        Spacer(modifier = Modifier.height(spacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = state.includeSearchHistory,
                onCheckedChange = { onEvent(ExportDataEvent.SearchHistoryToggled(it)) },
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = stringResource(R.string.export_option_search_history),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = state.includeAddons,
                onCheckedChange = { onEvent(ExportDataEvent.AddonsToggled(it)) },
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = stringResource(R.string.export_option_addons),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (state.showAddonsWarning) {
            Spacer(modifier = Modifier.height(spacing.xs))
            NexusCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Text(
                        text = stringResource(R.string.export_addons_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        Text(
            text = stringResource(R.string.export_unencrypted_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        NexusButton(
            onClick = { onEvent(ExportDataEvent.ExportClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.export_button))
        }
    }
}

@Composable
private fun BulletItem(text: String) {
    val spacing = NexusTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = spacing.xxs),
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun WritingContent() {
    val spacing = NexusTheme.spacing
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.height(PROGRESS_INDICATOR_SIZE))
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                text = stringResource(R.string.export_exporting),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DoneContent(
    summary: ExportSummary,
    onDone: () -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.export_done),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(spacing.md))

            BulletItem(pluralStringResource(R.plurals.export_summary_profiles, summary.profiles, summary.profiles))
            BulletItem(
                pluralStringResource(
                    R.plurals.export_summary_library,
                    summary.libraryItems,
                    summary.libraryItems,
                ),
            )
            BulletItem(
                pluralStringResource(
                    R.plurals.export_summary_history,
                    summary.progressEntries,
                    summary.progressEntries,
                ),
            )
            if (summary.searches > 0) {
                BulletItem(
                    pluralStringResource(
                        R.plurals.export_summary_searches,
                        summary.searches,
                        summary.searches,
                    ),
                )
            }
            if (summary.addons > 0) {
                BulletItem(pluralStringResource(R.plurals.export_summary_addons, summary.addons, summary.addons))
            }
        }

        NexusButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.export_done))
        }
    }
}

@Composable
private fun ErrorContent(
    isTooLarge: Boolean,
    onRetry: () -> Unit,
) {
    val message =
        if (isTooLarge) {
            stringResource(R.string.export_error_too_large)
        } else {
            stringResource(R.string.export_error_generic)
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        NexusButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.export_try_again))
        }
    }
}
