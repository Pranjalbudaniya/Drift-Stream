package com.drift.stream.domain.repository

import com.drift.stream.domain.model.HomeRowConfig
import kotlinx.coroutines.flow.Flow

interface HomeRowConfigRepository {
    val config: Flow<HomeRowConfig>

    suspend fun update(transform: (HomeRowConfig) -> HomeRowConfig)

    suspend fun reset()
}
