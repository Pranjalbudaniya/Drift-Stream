package com.drift.stream.domain.repository

import com.drift.stream.domain.model.ContinueWatchingItem
import com.drift.stream.domain.model.EpisodeProgress
import com.drift.stream.domain.model.EpisodeRef
import com.drift.stream.domain.model.MetaPreview
import com.drift.stream.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    suspend fun get(key: String): WatchProgress?

    suspend fun upsert(progress: WatchProgress)

    fun observeContinueWatching(): Flow<List<ContinueWatchingItem>>

    suspend fun removeFromContinueWatching(metaId: String)

    fun observeEpisodeProgress(metaId: String): Flow<Map<String, EpisodeProgress>>

    suspend fun setWatched(
        meta: MetaPreview,
        episode: EpisodeRef,
        watched: Boolean,
    )

    suspend fun setWatchedMany(
        meta: MetaPreview,
        episodes: List<EpisodeRef>,
        watched: Boolean,
    )

    suspend fun clearInProgress(metaId: String)

    fun observeWatchedMovieIds(): Flow<Set<String>>
}
