package com.drift.stream.data.local.db.dao

import com.drift.stream.data.local.db.entity.LibraryItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryDao : LibraryDao {
    val items = MutableStateFlow<Map<String, LibraryItemEntity>>(emptyMap())

    private fun libraryKey(
        profileId: Long,
        metaId: String,
    ): String = if (profileId == 1L) metaId else "$profileId|$metaId"

    override fun observeListStatuses(profileId: Long): Flow<List<LibraryStatusTuple>> =
        items.map { map ->
            map.values
                .filter { it.profileId == profileId && it.inWatchlist }
                .map { LibraryStatusTuple(metaId = it.metaId, listStatus = it.listStatus) }
        }

    override fun observeWatchlist(profileId: Long): Flow<List<LibraryItemEntity>> =
        items.map { map ->
            map.values.filter { it.profileId == profileId && it.inWatchlist }
        }

    override suspend fun get(
        profileId: Long,
        metaId: String,
    ): LibraryItemEntity? = items.value[libraryKey(profileId, metaId)] ?: items.value["$profileId|$metaId"]

    suspend fun get(metaId: String): LibraryItemEntity? = get(1L, metaId)

    override suspend fun getAllForProfile(profileId: Long): List<LibraryItemEntity> =
        items.value.values.filter { it.profileId == profileId }

    override suspend fun upsert(entity: LibraryItemEntity) {
        val current = items.value.toMutableMap()
        current[libraryKey(entity.profileId, entity.metaId)] = entity
        items.value = current
    }

    override suspend fun upsertAll(entities: List<LibraryItemEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun deleteAllForProfile(profileId: Long) {
        val current = items.value.toMutableMap()
        current.entries.removeIf { it.value.profileId == profileId }
        items.value = current
    }
}
