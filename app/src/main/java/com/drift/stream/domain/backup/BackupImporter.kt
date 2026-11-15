package com.drift.stream.domain.backup

sealed interface ImportMode {
    data object AddAsNew : ImportMode

    data class MergeInto(
        val profileId: Long,
    ) : ImportMode

    data class ReplaceIn(
        val profileId: Long,
    ) : ImportMode
}

data class ImportRequest(
    val mode: ImportMode,
    val selectedProfileIndexes: List<Int>,
    val includeAddons: Boolean,
    val skipped: Int = 0,
)

sealed interface ImportResult {
    data class Done(
        val summary: ImportSummary,
    ) : ImportResult

    data object TargetNotFound : ImportResult

    data object NothingSelected : ImportResult

    data class Failed(
        val cause: Throwable,
    ) : ImportResult
}

data class ImportSummary(
    val profilesCreated: Int,
    val libraryItems: Int,
    val progressEntries: Int,
    val searches: Int,
    val addons: Int,
    val skipped: Int,
    val warnings: List<ImportWarning>,
)

enum class ImportWarning {
    PhotoNotSaved,
    SettingsNotFullySaved,
}

interface BackupImporter {
    suspend fun import(
        backup: ValidatedBackup,
        request: ImportRequest,
    ): ImportResult
}
