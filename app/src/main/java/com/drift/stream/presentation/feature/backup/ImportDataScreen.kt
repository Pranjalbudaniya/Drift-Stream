package com.drift.stream.presentation.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.domain.backup.ImportSummary
import com.drift.stream.domain.backup.ImportWarning
import com.drift.stream.domain.backup.PreviewProfile
import com.drift.stream.domain.backup.RejectReason
import com.drift.stream.presentation.common.NexusButton
import com.drift.stream.presentation.common.NexusCard
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.common.NexusTextButton
import com.drift.stream.presentation.theme.NexusTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PROGRESS_INDICATOR_SIZE = 48.dp
private val MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

@Composable
fun ImportDataScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                viewModel.onEvent(ImportDataEvent.FileChosen(uri))
            },
        )

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportDataEffect.LaunchPicker -> {
                    filePickerLauncher.launch(MIME_TYPES)
                }

                is ImportDataEffect.Close -> {
                    onBack()
                }
            }
        }
    }

    ImportDataContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataContent(
    state: ImportDataUiState,
    onEvent: (ImportDataEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val title =
        if (state.isAddAsNew) {
            stringResource(R.string.import_title_add_as_new)
        } else {
            stringResource(R.string.import_title_into, state.targetProfileName)
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
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
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val status = state.status) {
                is ImportStatus.Choose -> {
                    ChooseStep(onEvent = onEvent)
                }

                is ImportStatus.Parsing -> {
                    ProgressStep(text = stringResource(R.string.import_parsing))
                }

                is ImportStatus.Rejected -> {
                    RejectedStep(reason = status.reason, onEvent = onEvent)
                }

                is ImportStatus.Preview, is ImportStatus.ConfirmReplace -> {
                    PreviewStep(
                        state = state,
                        onEvent = onEvent,
                        showDialog = status is ImportStatus.ConfirmReplace,
                    )
                }

                is ImportStatus.Importing -> {
                    ProgressStep(text = stringResource(R.string.import_importing))
                }

                is ImportStatus.Summary -> {
                    SummaryStep(summary = status.summary, onEvent = onEvent)
                }

                is ImportStatus.Error -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.import_error_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(spacing.sm))
                        NexusButton(
                            onClick = { onEvent(ImportDataEvent.Retry) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.import_try_again_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseStep(onEvent: (ImportDataEvent) -> Unit) {
    val spacing = NexusTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.import_choose_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        NexusButton(
            onClick = { onEvent(ImportDataEvent.ChooseFileClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_choose_file_button))
        }
    }
}

@Composable
private fun ProgressStep(text: String) {
    val spacing = NexusTheme.spacing
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(PROGRESS_INDICATOR_SIZE))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RejectedStep(
    reason: RejectReason,
    onEvent: (ImportDataEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    val message =
        when (reason) {
            RejectReason.NotABackup -> stringResource(R.string.import_rejected_not_a_backup)
            RejectReason.TooLarge -> stringResource(R.string.import_rejected_too_large)
            RejectReason.NewerVersion -> stringResource(R.string.import_rejected_newer_version)
            RejectReason.Corrupt -> stringResource(R.string.import_rejected_corrupt)
            RejectReason.NoProfiles -> stringResource(R.string.import_rejected_no_profiles)
            RejectReason.TooManyItems -> stringResource(R.string.import_rejected_too_many_items)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        NexusButton(
            onClick = { onEvent(ImportDataEvent.ChooseAnotherFileClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_choose_another_file))
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportDataUiState,
    onEvent: (ImportDataEvent) -> Unit,
    showDialog: Boolean,
) {
    val spacing = NexusTheme.spacing
    val preview = state.preview ?: return

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        val dateText = formatCreatedAt(preview.createdAt)
        Text(
            text = stringResource(R.string.import_preview_header, dateText, preview.appVersion),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.isAddAsNew) {
            AddAsNewProfilesList(
                profiles = preview.profiles,
                selected = state.selectedProfileIndexes,
                onToggle = { onEvent(ImportDataEvent.ProfileToggled(it)) },
            )
        } else {
            IntoProfileOptions(
                state = state,
                onEvent = onEvent,
            )
        }

        if (state.hasAddons) {
            val totalAddons =
                if (state.isAddAsNew) {
                    preview.profiles.filter { it.index in state.selectedProfileIndexes }.sumOf { it.addonCount ?: 0 }
                } else {
                    preview.profiles.getOrNull(state.selectedSourceIndex)?.addonCount ?: 0
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) {
                            onEvent(ImportDataEvent.AddonsToggled(!state.includeAddons))
                        }.padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.includeAddons,
                    onCheckedChange = { onEvent(ImportDataEvent.AddonsToggled(it)) },
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                Text(
                    text = stringResource(R.string.import_addons_checkbox, totalAddons),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.sm))
        val canImport = if (state.isAddAsNew) state.selectedProfileIndexes.isNotEmpty() else true
        NexusButton(
            onClick = { onEvent(ImportDataEvent.ImportClicked) },
            enabled = canImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_button))
        }
    }

    if (showDialog) {
        ConfirmReplaceDialog(
            targetName = state.targetProfileName,
            onConfirm = { onEvent(ImportDataEvent.ReplaceConfirmed) },
            onDismiss = { onEvent(ImportDataEvent.ReplaceDismissed) },
        )
    }
}

@Composable
private fun AddAsNewProfilesList(
    profiles: List<PreviewProfile>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        for (prof in profiles) {
            NexusCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) { onToggle(prof.index) }
                            .padding(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = prof.index in selected,
                        onCheckedChange = { onToggle(prof.index) },
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column {
                        Text(
                            text = prof.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatProfileCounts(prof),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntoProfileOptions(
    state: ImportDataUiState,
    onEvent: (ImportDataEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    val preview = state.preview ?: return

    if (preview.profiles.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            for (prof in preview.profiles) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = prof.index == state.selectedSourceIndex,
                                role = Role.RadioButton,
                                onClick = { onEvent(ImportDataEvent.SourceProfileSelected(prof.index)) },
                            ).padding(vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = prof.index == state.selectedSourceIndex,
                        onClick = { onEvent(ImportDataEvent.SourceProfileSelected(prof.index)) },
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column {
                        Text(text = prof.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = formatProfileCounts(prof),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(spacing.sm))
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.intoMode == SelectedMode.Merge,
                        role = Role.RadioButton,
                        onClick = { onEvent(ImportDataEvent.IntoModeSelected(SelectedMode.Merge)) },
                    ).padding(vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = state.intoMode == SelectedMode.Merge,
                onClick = { onEvent(ImportDataEvent.IntoModeSelected(SelectedMode.Merge)) },
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.import_mode_merge, state.targetProfileName),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.intoMode == SelectedMode.Replace,
                        role = Role.RadioButton,
                        onClick = { onEvent(ImportDataEvent.IntoModeSelected(SelectedMode.Replace)) },
                    ).padding(vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = state.intoMode == SelectedMode.Replace,
                onClick = { onEvent(ImportDataEvent.IntoModeSelected(SelectedMode.Replace)) },
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.import_mode_replace, state.targetProfileName),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ConfirmReplaceDialog(
    targetName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_replace_dialog_title, targetName)) },
        text = { Text(stringResource(R.string.import_replace_dialog_body)) },
        confirmButton = {
            NexusButton(onClick = onConfirm) {
                Text(stringResource(R.string.import_replace_dialog_confirm))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.import_replace_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SummaryStep(
    summary: ImportSummary,
    onEvent: (ImportDataEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = formatSummaryText(summary),
            style = MaterialTheme.typography.bodyLarge,
        )

        for (warning in summary.warnings) {
            val warnText =
                when (warning) {
                    ImportWarning.PhotoNotSaved -> stringResource(R.string.import_warning_photo)
                    ImportWarning.SettingsNotFullySaved -> stringResource(R.string.import_warning_settings)
                }
            Text(
                text = warnText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(spacing.sm))
        NexusButton(
            onClick = { onEvent(ImportDataEvent.DoneClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_done_button))
        }
    }
}
