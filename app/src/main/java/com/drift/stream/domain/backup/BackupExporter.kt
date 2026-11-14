package com.drift.stream.domain.backup

import java.io.OutputStream

data class ExportOptions(
    val includeSearchHistory: Boolean,
    val includeAddons: Boolean,
)

data class ExportSummary(
    val profiles: Int,
    val libraryItems: Int,
    val progressEntries: Int,
    val searches: Int,
    val addons: Int,
)

sealed interface ExportResult {
    data class Done(
        val summary: ExportSummary,
    ) : ExportResult

    data object NotFound : ExportResult

    data object TooLarge : ExportResult

    data class Failed(
        val cause: Throwable? = null,
    ) : ExportResult
}

interface BackupExporter {
    suspend fun export(
        scope: ExportScope,
        options: ExportOptions,
        sink: OutputStream,
    ): ExportResult
}
