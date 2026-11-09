package com.drift.stream.data.profile

import com.drift.stream.domain.repository.ActiveProfileProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withTimeoutOrNull

private const val PROFILE_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> ActiveProfileProvider.scopedFlow(block: (Long) -> Flow<T>): Flow<T> =
    activeProfileId.filterNotNull().distinctUntilChanged().flatMapLatest(block)

suspend fun ActiveProfileProvider.requireId(): Long =
    withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
        activeProfileId.filterNotNull().first()
    } ?: error("No active profile")
