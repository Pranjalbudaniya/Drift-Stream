package com.drift.stream.data.local.db.dao

import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.domain.model.ContinueRow
import com.drift.stream.domain.model.ListStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FakeProgressDao(
    private val libraryDao: FakeLibraryDao? = null,
) : ProgressDao {
    val progressEntities = MutableStateFlow<Map<String, WatchProgressEntity>>(emptyMap())
    val continueRows = MutableStateFlow<List<ContinueRow>>(emptyList())
    val deletedMetaIds = mutableListOf<String>()
    val deletedKeys = mutableListOf<String>()

    private fun progressKey(
        profileId: Long,
        key: String,
    ): String = if (profileId == 1L) key else "$profileId|$key"

    override fun observeContinueRows(profileId: Long): Flow<List<ContinueRow>> {
        val libDao = libraryDao
        if (libDao != null) {
            return combine(progressEntities, libDao.items) { progressMap, libraryMap ->
                val profileProgress = progressMap.values.filter { it.profileId == profileId }
                val byMeta = profileProgress.groupBy { it.metaId }
                byMeta.values
                    .mapNotNull { rows ->
                        val newest = rows.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                        val libraryItem =
                            libraryMap[
                                if (newest.profileId ==
                                    1L
                                ) {
                                    newest.metaId
                                } else {
                                    "${newest.profileId}|${newest.metaId}"
                                },
                            ]
                                ?: libraryMap["${newest.profileId}|${newest.metaId}"]
                        ContinueRow(
                            metaId = newest.metaId,
                            type = newest.type,
                            name = libraryItem?.name.orEmpty(),
                            poster = libraryItem?.poster,
                            videoId = newest.videoId,
                            season = newest.season,
                            episode = newest.episode,
                            positionMs = newest.positionMs,
                            durationMs = newest.durationMs,
                            watched = newest.watched,
                            updatedAt = newest.updatedAt,
                            inWatchlist = libraryItem?.inWatchlist ?: false,
                            listStatus =
                                libraryItem?.listStatus?.let {
                                    runCatching { ListStatus.valueOf(it) }.getOrNull()
                                },
                        )
                    }.sortedByDescending { it.updatedAt }
                    .take(40)
            }
        }
        return continueRows
    }

    override suspend fun get(
        profileId: Long,
        key: String,
    ): WatchProgressEntity? =
        progressEntities.value[progressKey(profileId, key)] ?: progressEntities.value["$profileId|$key"]

    suspend fun get(key: String): WatchProgressEntity? = get(1L, key)

    override suspend fun upsert(entity: WatchProgressEntity) {
        val current = progressEntities.value.toMutableMap()
        current[progressKey(entity.profileId, entity.key)] = entity
        progressEntities.value = current
    }

    override suspend fun upsertAll(entities: List<WatchProgressEntity>) {
        val current = progressEntities.value.toMutableMap()
        for (entity in entities) {
            current[progressKey(entity.profileId, entity.key)] = entity
        }
        progressEntities.value = current
    }

    override suspend fun deleteByMeta(
        profileId: Long,
        metaId: String,
    ) {
        deletedMetaIds.add(metaId)
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf { it.value.profileId == profileId && it.value.metaId == metaId }
        progressEntities.value = current

        continueRows.value = continueRows.value.filter { it.metaId != metaId }
    }

    override fun observeByMeta(
        profileId: Long,
        metaId: String,
    ): Flow<List<WatchProgressEntity>> =
        progressEntities.map { map ->
            map.values.filter { it.profileId == profileId && it.metaId == metaId }
        }

    override suspend fun deleteByKey(
        profileId: Long,
        key: String,
    ) {
        deletedKeys.add(key)
        val current = progressEntities.value.toMutableMap()
        current.remove(progressKey(profileId, key))
        current.entries.removeIf { it.value.profileId == profileId && it.value.key == key }
        progressEntities.value = current
    }

    override suspend fun clearWatched(
        profileId: Long,
        metaId: String,
        videoIds: List<String>,
    ) {
        deletedKeys.addAll(videoIds.map { "$metaId|$it" })
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf {
            it.value.profileId == profileId && it.value.metaId == metaId && it.value.videoId in videoIds
        }
        progressEntities.value = current
    }

    override suspend fun clearInProgress(
        profileId: Long,
        metaId: String,
    ) {
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf { it.value.profileId == profileId && it.value.metaId == metaId && !it.value.watched }
        progressEntities.value = current

        val remainingForMeta = current.values.filter { it.profileId == profileId && it.metaId == metaId }
        val newest = remainingForMeta.maxByOrNull { it.updatedAt }
        val existingRow = continueRows.value.firstOrNull { it.metaId == metaId }
        val updatedList = continueRows.value.filter { it.metaId != metaId }.toMutableList()
        if (newest != null && existingRow != null) {
            updatedList.add(
                existingRow.copy(
                    videoId = newest.videoId,
                    season = newest.season,
                    episode = newest.episode,
                    positionMs = newest.positionMs,
                    durationMs = newest.durationMs,
                    watched = newest.watched,
                    updatedAt = newest.updatedAt,
                ),
            )
        }
        continueRows.value = updatedList
    }

    override fun observeWatchedMovieIds(profileId: Long): Flow<List<String>> =
        progressEntities.map { map ->
            map.values
                .filter { it.profileId == profileId && it.watched && it.type.equals("movie", ignoreCase = true) }
                .map { it.metaId }
        }

    override fun observeHistoryRows(
        profileId: Long,
        limit: Int,
    ): Flow<List<ContinueRow>> {
        val libDao = libraryDao
        if (libDao != null) {
            return combine(progressEntities, libDao.items) { progressMap, libraryMap ->
                val profileProgress = progressMap.values.filter { it.profileId == profileId }
                val byMeta = profileProgress.groupBy { it.metaId }
                byMeta.values
                    .mapNotNull { rows ->
                        val newest = rows.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                        val libraryItem =
                            libraryMap[
                                if (newest.profileId ==
                                    1L
                                ) {
                                    newest.metaId
                                } else {
                                    "${newest.profileId}|${newest.metaId}"
                                },
                            ]
                                ?: libraryMap["${newest.profileId}|${newest.metaId}"]
                        ContinueRow(
                            metaId = newest.metaId,
                            type = newest.type,
                            name = libraryItem?.name,
                            poster = libraryItem?.poster,
                            videoId = newest.videoId,
                            season = newest.season,
                            episode = newest.episode,
                            positionMs = newest.positionMs,
                            durationMs = newest.durationMs,
                            watched = newest.watched,
                            updatedAt = newest.updatedAt,
                            inWatchlist = libraryItem?.inWatchlist ?: false,
                            listStatus =
                                libraryItem?.listStatus?.let {
                                    runCatching { ListStatus.valueOf(it) }.getOrNull()
                                },
                        )
                    }.sortedByDescending { it.updatedAt }
                    .take(limit)
            }
        }
        return continueRows.map { it.take(limit) }
    }

    override suspend fun deleteAll(profileId: Long) {
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf { it.value.profileId == profileId }
        progressEntities.value = current
        continueRows.value = emptyList()
    }

    override suspend fun deleteAllForProfile(profileId: Long) {
        deleteAll(profileId)
    }
}
