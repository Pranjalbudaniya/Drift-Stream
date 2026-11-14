package com.drift.stream.presentation.feature.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.backup.BackupExporter
import com.drift.stream.domain.backup.ExportOptions
import com.drift.stream.domain.backup.ExportResult
import com.drift.stream.domain.backup.ExportScope
import com.drift.stream.domain.backup.ExportSummary
import com.drift.stream.domain.backup.backupFileName
import com.drift.stream.domain.profile.canEditProfile
import com.drift.stream.domain.profile.canManageProfiles
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject

sealed interface ExportStatus {
    data object Options : ExportStatus

    data object Writing : ExportStatus

    data class Done(
        val summary: ExportSummary,
    ) : ExportStatus

    data class Error(
        val isTooLarge: Boolean = false,
    ) : ExportStatus
}

data class ExportDataUiState(
    val scope: ExportScope = ExportScope.All,
    val profileName: String = "",
    val includeSearchHistory: Boolean = true,
    val includeAddons: Boolean = false,
    val status: ExportStatus = ExportStatus.Options,
    val closeScreen: Boolean = false,
) {
    val showAddonsWarning: Boolean get() = includeAddons
}

sealed interface ExportDataEffect {
    data class LaunchFilePicker(
        val suggestedName: String,
    ) : ExportDataEffect

    data object Close : ExportDataEffect
}

sealed interface ExportDataEvent {
    data class SearchHistoryToggled(
        val enabled: Boolean,
    ) : ExportDataEvent

    data class AddonsToggled(
        val enabled: Boolean,
    ) : ExportDataEvent

    data object ExportClicked : ExportDataEvent

    data class FileChosen(
        val uri: Uri?,
    ) : ExportDataEvent

    data object Retry : ExportDataEvent

    data object DoneClicked : ExportDataEvent
}

data class ExportIoOps(
    val openStream: (Uri) -> OutputStream?,
    val deleteDocument: (Uri) -> Boolean,
    val dateProvider: () -> LocalDate = { LocalDate.now() },
)

@HiltViewModel
class ExportDataViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val backupExporter: BackupExporter,
    private val profileRepository: ProfileRepository,
    private val activeProfileHolder: ActiveProfileProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ioOps: ExportIoOps? = null,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        backupExporter: BackupExporter,
        profileRepository: ProfileRepository,
        activeProfileHolder: ActiveProfileProvider,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @ApplicationContext context: Context,
    ) : this(
        savedStateHandle = savedStateHandle,
        backupExporter = backupExporter,
        profileRepository = profileRepository,
        activeProfileHolder = activeProfileHolder,
        ioDispatcher = ioDispatcher,
        ioOps =
            ExportIoOps(
                openStream = { uri -> context.contentResolver.openOutputStream(uri, "wt") },
                deleteDocument = { uri ->
                    try {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    } catch (_: Exception) {
                        false
                    }
                },
            ),
    )

    private val currentScope: ExportScope
    private val _uiState = MutableStateFlow(ExportDataUiState())
    val uiState: StateFlow<ExportDataUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ExportDataEffect>(extraBufferCapacity = 64)
    val effects: SharedFlow<ExportDataEffect> = _effects.asSharedFlow()

    init {
        val profileId = savedStateHandle.get<Long>("profileId")
        currentScope =
            if (profileId != null && profileId > 0L) {
                ExportScope.Profile(profileId)
            } else {
                ExportScope.All
            }
        _uiState.value = _uiState.value.copy(scope = currentScope)

        viewModelScope.launch {
            val activeId = activeProfileHolder.activeProfileId.value
            val active = if (activeId != null) profileRepository.get(activeId) else null
            val allowed =
                when (val s = currentScope) {
                    is ExportScope.All -> {
                        canManageProfiles(active)
                    }

                    is ExportScope.Profile -> {
                        val target = profileRepository.get(s.profileId)
                        if (target != null) {
                            _uiState.update { it.copy(profileName = target.name) }
                            canEditProfile(target, active)
                        } else {
                            false
                        }
                    }
                }
            if (!allowed) {
                _effects.emit(ExportDataEffect.Close)
                _uiState.update { it.copy(closeScreen = true) }
            }
        }
    }

    fun onEvent(event: ExportDataEvent) {
        when (event) {
            is ExportDataEvent.SearchHistoryToggled -> {
                _uiState.update { it.copy(includeSearchHistory = event.enabled) }
            }

            is ExportDataEvent.AddonsToggled -> {
                _uiState.update { it.copy(includeAddons = event.enabled) }
            }

            is ExportDataEvent.ExportClicked -> {
                if (_uiState.value.status is ExportStatus.Writing) return
                val date = ioOps?.dateProvider?.invoke() ?: LocalDate.now()
                val suggestedName = backupFileName(currentScope, _uiState.value.profileName, date)
                viewModelScope.launch {
                    _effects.emit(ExportDataEffect.LaunchFilePicker(suggestedName))
                }
            }

            is ExportDataEvent.FileChosen -> {
                handleFileChosen(event.uri)
            }

            is ExportDataEvent.Retry -> {
                _uiState.update { it.copy(status = ExportStatus.Options) }
            }

            is ExportDataEvent.DoneClicked -> {
                viewModelScope.launch {
                    _effects.emit(ExportDataEffect.Close)
                }
                _uiState.update { it.copy(closeScreen = true) }
            }
        }
    }

    private fun handleFileChosen(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(status = ExportStatus.Options) }
            return
        }

        if (_uiState.value.status is ExportStatus.Writing) return
        _uiState.update { it.copy(status = ExportStatus.Writing) }

        viewModelScope.launch(ioDispatcher) {
            val sink =
                try {
                    ioOps?.openStream?.invoke(uri)
                } catch (_: Exception) {
                    null
                }

            if (sink == null) {
                safeDelete(uri)
                _uiState.update { it.copy(status = ExportStatus.Error(isTooLarge = false)) }
                return@launch
            }

            val options =
                ExportOptions(
                    includeSearchHistory = _uiState.value.includeSearchHistory,
                    includeAddons = _uiState.value.includeAddons,
                )

            val result =
                try {
                    sink.use { outputStream ->
                        backupExporter.export(currentScope, options, outputStream)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    safeDelete(uri)
                    _uiState.update { it.copy(status = ExportStatus.Error(isTooLarge = false)) }
                    return@launch
                } catch (_: RuntimeException) {
                    safeDelete(uri)
                    _uiState.update { it.copy(status = ExportStatus.Error(isTooLarge = false)) }
                    return@launch
                }

            when (result) {
                is ExportResult.Done -> {
                    _uiState.update { it.copy(status = ExportStatus.Done(result.summary)) }
                }

                is ExportResult.TooLarge -> {
                    safeDelete(uri)
                    _uiState.update { it.copy(status = ExportStatus.Error(isTooLarge = true)) }
                }

                is ExportResult.NotFound, is ExportResult.Failed -> {
                    safeDelete(uri)
                    _uiState.update { it.copy(status = ExportStatus.Error(isTooLarge = false)) }
                }
            }
        }
    }

    private fun safeDelete(uri: Uri) {
        try {
            ioOps?.deleteDocument?.invoke(uri)
        } catch (_: Exception) {
            // best-effort deletion, ignore errors
        }
    }
}
