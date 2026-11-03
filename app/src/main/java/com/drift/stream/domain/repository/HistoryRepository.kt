package com.drift.stream.domain.repository

import com.drift.stream.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(limit: Int): Flow<List<HistoryEntry>>

    suspend fun remove(metaId: String)

    suspend fun clear()
}
