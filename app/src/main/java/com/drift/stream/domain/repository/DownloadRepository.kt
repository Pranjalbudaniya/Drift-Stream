package com.drift.stream.domain.repository

import com.drift.stream.domain.model.DownloadFailure
import com.drift.stream.domain.model.DownloadItem
import com.drift.stream.domain.model.DownloadSource
import com.drift.stream.domain.model.DownloadState
import com.drift.stream.domain.model.GalleryFailure
import com.drift.stream.domain.model.GalleryState
import com.drift.stream.domain.model.PauseReason
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadItem>>

    fun observeForMeta(metaId: String): Flow<List<DownloadItem>>

    fun observeEverything(): Flow<List<DownloadItem>>

    suspend fun get(id: String): DownloadItem?

    suspend fun getAll(): List<DownloadItem>

    suspend fun getAllForActiveProfile(): List<DownloadItem>

    suspend fun insert(item: DownloadItem)

    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        state: DownloadState,
        updatedAt: Long,
    )

    suspend fun updateState(
        id: String,
        state: DownloadState,
        updatedAt: Long,
        pauseReason: PauseReason? = null,
        failure: DownloadFailure? = null,
    )

    suspend fun updateCompleted(
        id: String,
        filePath: String,
        totalBytes: Long,
        completedAt: Long,
        updatedAt: Long,
    )

    suspend fun updateSource(
        id: String,
        source: DownloadSource,
        state: DownloadState,
        updatedAt: Long,
    )

    suspend fun setAllowMetered(
        id: String,
        allowMetered: Boolean,
        updatedAt: Long,
    )

    suspend fun delete(id: String)
}
