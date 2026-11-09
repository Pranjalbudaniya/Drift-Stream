package com.drift.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.domain.model.ContinueRow
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface ProgressDao {
    @Query(
        """
        SELECT 
            p.metaId AS metaId,
            p.type AS type,
            l.name AS name,
            l.poster AS poster,
            p.videoId AS videoId,
            p.season AS season,
            p.episode AS episode,
            p.positionMs AS positionMs,
            p.durationMs AS durationMs,
            p.watched AS watched,
            p.updatedAt AS updatedAt,
            coalesce(l.inWatchlist, 0) AS inWatchlist,
            l.listStatus AS listStatus
        FROM watch_progress p
        LEFT JOIN library_items l ON p.metaId = l.metaId AND l.profileId = p.profileId
        WHERE p.profileId = :profileId AND p.key = (
            SELECT p2.key 
            FROM watch_progress p2 
            WHERE p2.metaId = p.metaId AND p2.profileId = p.profileId 
            ORDER BY p2.updatedAt DESC 
            LIMIT 1
        )
        ORDER BY p.updatedAt DESC
        LIMIT 40
        """,
    )
    fun observeContinueRows(profileId: Long): Flow<List<ContinueRow>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND `key` = :key LIMIT 1")
    suspend fun get(
        profileId: Long,
        key: String,
    ): WatchProgressEntity?

    @Upsert
    suspend fun upsert(entity: WatchProgressEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WatchProgressEntity>)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND metaId = :metaId")
    suspend fun deleteByMeta(
        profileId: Long,
        metaId: String,
    )

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND metaId = :metaId")
    fun observeByMeta(
        profileId: Long,
        metaId: String,
    ): Flow<List<WatchProgressEntity>>

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND `key` = :key")
    suspend fun deleteByKey(
        profileId: Long,
        key: String,
    )

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND metaId = :metaId AND videoId IN (:videoIds)")
    suspend fun clearWatched(
        profileId: Long,
        metaId: String,
        videoIds: List<String>,
    )

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND metaId = :metaId AND watched = 0")
    suspend fun clearInProgress(
        profileId: Long,
        metaId: String,
    )

    @Query("SELECT metaId FROM watch_progress WHERE profileId = :profileId AND watched = 1 AND type = 'movie'")
    fun observeWatchedMovieIds(profileId: Long): Flow<List<String>>

    @Query(
        """
        SELECT 
            p.metaId AS metaId,
            p.type AS type,
            l.name AS name,
            l.poster AS poster,
            p.videoId AS videoId,
            p.season AS season,
            p.episode AS episode,
            p.positionMs AS positionMs,
            p.durationMs AS durationMs,
            p.watched AS watched,
            p.updatedAt AS updatedAt,
            coalesce(l.inWatchlist, 0) AS inWatchlist,
            l.listStatus AS listStatus
        FROM watch_progress p
        JOIN (SELECT metaId AS mid, MAX(updatedAt) AS maxAt FROM watch_progress WHERE profileId = :profileId GROUP BY metaId) m
             ON p.metaId = m.mid AND p.updatedAt = m.maxAt
        LEFT JOIN library_items l ON l.metaId = p.metaId AND l.profileId = p.profileId
        WHERE p.profileId = :profileId
        ORDER BY p.updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeHistoryRows(
        profileId: Long,
        limit: Int,
    ): Flow<List<ContinueRow>>

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
}
