package com.drift.stream.data.local.db.dao

import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSearchHistoryDao : SearchHistoryDao {
    val entries = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())

    override fun observeAll(profileId: Long): Flow<List<SearchHistoryEntity>> =
        entries.map { list -> list.filter { it.profileId == profileId } }

    override suspend fun getAll(profileId: Long): List<SearchHistoryEntity> =
        entries.value.filter { it.profileId == profileId }

    override suspend fun upsert(entity: SearchHistoryEntity) {
        entries.update { list ->
            (list.filterNot { it.profileId == entity.profileId && it.query == entity.query } + entity)
                .sortedByDescending { it.lastUsedAt }
        }
    }

    override suspend fun upsertAll(entities: List<SearchHistoryEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun delete(
        profileId: Long,
        query: String,
    ) {
        entries.update { list ->
            list.filterNot { it.profileId == profileId && it.query == query }
        }
    }

    override suspend fun deleteAll(profileId: Long) {
        entries.update { list -> list.filterNot { it.profileId == profileId } }
    }

    override suspend fun deleteAllForProfile(profileId: Long) {
        deleteAll(profileId)
    }
}
