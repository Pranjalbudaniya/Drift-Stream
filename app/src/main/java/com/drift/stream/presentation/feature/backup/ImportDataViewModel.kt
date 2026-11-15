package com.drift.stream.presentation.feature.backup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.backup.BackupFileReader
import com.drift.stream.data.backup.FileReadResult
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.backup.BACKUP_MAX_BYTES
import com.drift.stream.domain.backup.BackupImporter
import com.drift.stream.domain.backup.BackupParser
import com.drift.stream.domain.backup.BackupPreview
import com.drift.stream.domain.backup.ImportMode
import com.drift.stream.domain.backup.ImportRequest
import com.drift.stream.domain.backup.ImportResult
import com.drift.stream.domain.backup.ImportSummary
import com.drift.stream.domain.backup.ParseOutcome
import com.drift.stream.domain.backup.RejectReason
import com.drift.stream.domain.backup.ValidatedBackup
import com.drift.stream.domain.profile.canEditProfile
import com.drift.stream.domain.profile.canManageProfiles
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ImportStatus {
    data object Choose : ImportStatus

    data object Parsing : ImportStatus

    data class Rejected(
        val reason: RejectReason,
    ) : ImportStatus

    data object Preview : ImportStatus

    data object ConfirmReplace : ImportStatus

    data object Importing : ImportStatus

    data class Summary(
        val summary: ImportSummary,
    ) : ImportStatus

    data object Error : ImportStatus
}

sealed interface SelectedMode {
    data object Merge : SelectedMode

    data object Replace : SelectedMode
}

data class ImportDataUiState(
    val isAddAsNew: Boolean = true,
    val targetProfileName: String = "",
    val status: ImportStatus = ImportStatus.Choose,
    val preview: BackupPreview? = null,
    val selectedProfileIndexes: Set<Int> = emptySet(),
    val selectedSourceIndex: Int = 0,
    val intoMode: SelectedMode = SelectedMode.Merge,
    val includeAddons: Boolean = true,
    val closeScreen: Boolean = false,
) {
    val hasAddons: Boolean
        get() {
            val p = preview ?: return false
            return if (isAddAsNew) {
                selectedProfileIndexes.any { idx ->
                    val prof = p.profiles.getOrNull(idx)
                    (prof?.addonCount ?: 0) > 0
                }
            } else {
                val prof = p.profiles.getOrNull(selectedSourceIndex)
                (prof?.addonCount ?: 0) > 0
            }
        }
}

sealed interface ImportDataEffect {
    data object LaunchPicker : ImportDataEffect

    data object Close : ImportDataEffect
}

sealed interface ImportDataEvent {
    data object ChooseFileClicked : ImportDataEvent

    data class FileChosen(
        val uri: Uri?,
    ) : ImportDataEvent

    data class ProfileToggled(
        val index: Int,
    ) : ImportDataEvent

    data class SourceProfileSelected(
        val index: Int,
    ) : ImportDataEvent

    data class IntoModeSelected(
        val mode: SelectedMode,
    ) : ImportDataEvent

    data class AddonsToggled(
        val enabled: Boolean,
    ) : ImportDataEvent

    data object ImportClicked : ImportDataEvent

    data object ReplaceConfirmed : ImportDataEvent

    data object ReplaceDismissed : ImportDataEvent

    data object Retry : ImportDataEvent

    data object DoneClicked : ImportDataEvent

    data object ChooseAnotherFileClicked : ImportDataEvent
}

@HiltViewModel
class ImportDataViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val backupFileReader: BackupFileReader,
        private val backupImporter: BackupImporter,
        private val profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val targetProfileId: Long? = savedStateHandle.get<Long>("profileId")?.takeIf { it > 0L }
        private val isAddAsNew: Boolean = targetProfileId == null
        private var parsedBackup: ValidatedBackup? = null

        private val _uiState = MutableStateFlow(ImportDataUiState(isAddAsNew = isAddAsNew))
        val uiState: StateFlow<ImportDataUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<ImportDataEffect>(extraBufferCapacity = 64)
        val effects: SharedFlow<ImportDataEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                checkPermissions()
            }
        }

        private suspend fun checkPermissions() {
            val activeId = activeProfileHolder.activeProfileId.value
            val active = if (activeId != null) profileRepository.get(activeId) else null
            val allowed =
                if (isAddAsNew) {
                    canManageProfiles(active)
                } else {
                    val target = targetProfileId?.let { profileRepository.get(it) }
                    if (target != null) {
                        _uiState.update { it.copy(targetProfileName = target.name) }
                        canEditProfile(target, active)
                    } else {
                        false
                    }
                }
            if (!allowed) {
                _effects.emit(ImportDataEffect.Close)
                _uiState.update { it.copy(closeScreen = true) }
            }
        }

        fun onEvent(event: ImportDataEvent) {
            when (event) {
                is ImportDataEvent.ChooseFileClicked, is ImportDataEvent.ChooseAnotherFileClicked -> {
                    _effects.tryEmit(ImportDataEffect.LaunchPicker)
                }

                is ImportDataEvent.FileChosen -> {
                    onFileChosen(event.uri)
                }

                is ImportDataEvent.ProfileToggled -> {
                    onProfileToggled(event.index)
                }

                is ImportDataEvent.SourceProfileSelected -> {
                    _uiState.update { it.copy(selectedSourceIndex = event.index) }
                }

                is ImportDataEvent.IntoModeSelected -> {
                    _uiState.update { it.copy(intoMode = event.mode) }
                }

                is ImportDataEvent.AddonsToggled -> {
                    _uiState.update { it.copy(includeAddons = event.enabled) }
                }

                is ImportDataEvent.ImportClicked -> {
                    onImportClicked()
                }

                is ImportDataEvent.ReplaceConfirmed -> {
                    onReplaceConfirmed()
                }

                is ImportDataEvent.ReplaceDismissed -> {
                    _uiState.update { it.copy(status = ImportStatus.Preview) }
                }

                is ImportDataEvent.Retry -> {
                    onRetry()
                }

                is ImportDataEvent.DoneClicked -> {
                    _effects.tryEmit(ImportDataEffect.Close)
                    _uiState.update { it.copy(closeScreen = true) }
                }
            }
        }

        private fun onFileChosen(uri: Uri?) {
            if (uri == null) return
            _uiState.update { it.copy(status = ImportStatus.Parsing) }
            viewModelScope.launch {
                val readResult = backupFileReader.read(uri, BACKUP_MAX_BYTES.toLong())
                val parseOutcome =
                    withContext(ioDispatcher) {
                        when (readResult) {
                            is FileReadResult.Success -> {
                                BackupParser.parse(readResult.bytes)
                            }

                            is FileReadResult.TooLarge -> {
                                ParseOutcome.Rejected(RejectReason.TooLarge)
                            }

                            is FileReadResult.Error -> {
                                ParseOutcome.Rejected(RejectReason.Corrupt)
                            }
                        }
                    }
                handleParseOutcome(parseOutcome)
            }
        }

        private fun handleParseOutcome(outcome: ParseOutcome) {
            when (outcome) {
                is ParseOutcome.Ready -> {
                    parsedBackup = outcome.backup
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Preview,
                            preview = outcome.preview,
                            selectedProfileIndexes =
                                outcome.preview.profiles.indices
                                    .toSet(),
                            selectedSourceIndex = 0,
                            intoMode = SelectedMode.Merge,
                            includeAddons = true,
                        )
                    }
                }

                is ParseOutcome.Rejected -> {
                    parsedBackup = null
                    _uiState.update { it.copy(status = ImportStatus.Rejected(outcome.reason)) }
                }
            }
        }

        private fun onProfileToggled(index: Int) {
            _uiState.update { state ->
                val updated =
                    if (index in state.selectedProfileIndexes) {
                        state.selectedProfileIndexes - index
                    } else {
                        state.selectedProfileIndexes + index
                    }
                state.copy(selectedProfileIndexes = updated)
            }
        }

        private fun onImportClicked() {
            val state = _uiState.value
            if (state.status !is ImportStatus.Preview) return
            if (state.isAddAsNew) {
                if (state.selectedProfileIndexes.isNotEmpty()) {
                    performImport()
                }
            } else {
                if (state.intoMode == SelectedMode.Replace) {
                    _uiState.update { it.copy(status = ImportStatus.ConfirmReplace) }
                } else {
                    performImport()
                }
            }
        }

        private fun onReplaceConfirmed() {
            if (_uiState.value.status !is ImportStatus.ConfirmReplace) return
            performImport()
        }

        private fun onRetry() {
            if (_uiState.value.status is ImportStatus.Error) {
                _uiState.update { it.copy(status = ImportStatus.Preview) }
            }
        }

        private fun performImport() {
            val backup = parsedBackup ?: return
            val state = _uiState.value
            _uiState.update { it.copy(status = ImportStatus.Importing) }

            val request =
                if (state.isAddAsNew) {
                    ImportRequest(
                        mode = ImportMode.AddAsNew,
                        selectedProfileIndexes = state.selectedProfileIndexes.sorted(),
                        includeAddons = state.includeAddons && state.hasAddons,
                    )
                } else {
                    val targetId = targetProfileId ?: return
                    val mode =
                        if (state.intoMode == SelectedMode.Merge) {
                            ImportMode.MergeInto(targetId)
                        } else {
                            ImportMode.ReplaceIn(targetId)
                        }
                    ImportRequest(
                        mode = mode,
                        selectedProfileIndexes = listOf(state.selectedSourceIndex),
                        includeAddons = state.includeAddons && state.hasAddons,
                    )
                }

            viewModelScope.launch {
                val result = backupImporter.import(backup, request)
                _uiState.update {
                    when (result) {
                        is ImportResult.Done -> it.copy(status = ImportStatus.Summary(result.summary))
                        else -> it.copy(status = ImportStatus.Error)
                    }
                }
            }
        }
    }
