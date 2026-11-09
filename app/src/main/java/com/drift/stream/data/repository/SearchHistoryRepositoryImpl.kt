package com.drift.stream.data.repository

import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.profile.requireId
import com.drift.stream.data.profile.scopedFlow
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.SearchHistoryRepository
import com.drift.stream.domain.usecase.HISTORY_LIMIT
import com.drift.stream.domain.usecase.isSearchable
import com.drift.stream.domain.usecase.normalizeSearchQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchHistoryRepositoryImpl(
    private val dao: SearchHistoryDao,
    private val activeProfile: ActiveProfileProvider,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SearchHistoryRepository {
    @Inject
    constructor(
        dao: SearchHistoryDao,
        activeProfile: ActiveProfileProvider,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        dao = dao,
        activeProfile = activeProfile,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override fun observeHistory(): Flow<List<String>> =
        activeProfile
            .scopedFlow { profileId ->
                dao
                    .observeAll(profileId)
                    .map { list -> list.map { it.query } }
            }.flowOn(dispatcher)

    override suspend fun save(query: String): Unit =
        withContext(dispatcher) {
            val profileId = activeProfile.requireId()
            val normalized = normalizeSearchQuery(query)
            if (!isSearchable(normalized)) return@withContext

            val all = dao.getAll(profileId)
            val existing = all.filter { it.query.equals(normalized, ignoreCase = true) }
            for (entry in existing) {
                if (entry.query != normalized) {
                    dao.delete(profileId, entry.query)
                }
            }

            val now = clock()
            dao.upsert(
                SearchHistoryEntity(
                    query = normalized,
                    lastUsedAt = now,
                    profileId = profileId,
                ),
            )

            val updated = dao.getAll(profileId)
            if (updated.size > HISTORY_LIMIT) {
                val toRemove = updated.drop(HISTORY_LIMIT)
                for (entry in toRemove) {
                    dao.delete(profileId, entry.query)
                }
            }
        }

    override suspend fun remove(query: String): Unit =
        withContext(dispatcher) {
            val profileId = activeProfile.requireId()
            val trimmed = query.trim()
            val all = dao.getAll(profileId)
            val matching = all.filter { it.query.equals(trimmed, ignoreCase = true) }
            for (entry in matching) {
                dao.delete(profileId, entry.query)
            }
        }

    override suspend fun clear(): Unit =
        withContext(dispatcher) {
            val profileId = activeProfile.requireId()
            dao.deleteAll(profileId)
        }
}
