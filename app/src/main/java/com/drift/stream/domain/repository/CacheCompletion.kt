package com.drift.stream.domain.repository

interface CacheCompletion {
    suspend fun onWatched(key: String)
}
