package com.drift.stream.domain.repository

interface TorrentCache {
    suspend fun cacheSizeBytes(): Long

    suspend fun clearCache()
}
