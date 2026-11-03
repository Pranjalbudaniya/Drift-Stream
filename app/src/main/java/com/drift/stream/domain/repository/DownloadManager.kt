package com.drift.stream.domain.repository

import com.drift.stream.domain.model.DownloadEffect
import com.drift.stream.domain.model.DownloadItem
import com.drift.stream.domain.model.DownloadLive
import com.drift.stream.domain.model.DownloadRequest
import com.drift.stream.domain.model.DownloadSource
import com.drift.stream.domain.model.EnqueueResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Suppress("TooManyFunctions")
interface DownloadManager {
    val items: Flow<List<DownloadItem>>
    val live: StateFlow<Map<String, DownloadLive>>
    val effects: Flow<DownloadEffect>

    suspend fun enqueue(request: DownloadRequest): EnqueueResult

    fun pause(id: String)

    fun resume(id: String)

    fun pauseAll()

    fun resumeAll()

    fun allowMobileData(id: String?)

    suspend fun cancel(id: String)

    suspend fun delete(id: String)

    suspend fun deleteCompleted()

    suspend fun replaceSource(
        id: String,
        source: DownloadSource,
    )

    suspend fun onAppStart()

    suspend fun onServiceTimeout()

    suspend fun verifyCompleted(id: String): Boolean

    fun fileOf(id: String): File?

    suspend fun moveToGallery(id: String)

    suspend fun moveAllToGallery()

    suspend fun selectTorrentFile(
        id: String,
        fileIndex: Int,
    )

    fun getTorrentFileChoices(id: String): List<TorrentFileChoice> = emptyList()
}
