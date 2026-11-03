package com.drift.stream.domain.repository

import com.drift.stream.domain.model.GalleryFailure
import java.io.File

sealed interface GalleryExportResult {
    data class Saved(
        val uri: String,
        val filePath: String,
    ) : GalleryExportResult

    data class Failed(
        val failure: GalleryFailure,
    ) : GalleryExportResult
}

interface GalleryExporter {
    suspend fun export(
        file: File,
        displayName: String,
        mimeType: String,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onRowCreated: (suspend (uri: String) -> Unit)? = null,
    ): GalleryExportResult

    suspend fun exists(uri: String): Boolean

    suspend fun resolvePath(uri: String): String?

    suspend fun delete(uri: String): Boolean

    suspend fun cleanupPending()
}
