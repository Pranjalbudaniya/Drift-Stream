package com.drift.stream.domain.repository

import com.drift.stream.domain.model.WatchlistEntry
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    fun observeWatchlist(): Flow<List<WatchlistEntry>>
}
