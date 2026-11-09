package com.drift.stream.data.repository

import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.profile.requireId
import com.drift.stream.data.profile.scopedFlow
import com.drift.stream.domain.model.ContinueRow
import com.drift.stream.domain.model.HistoryEntry
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.HistoryRepository
import com.drift.stream.domain.usecase.latestPerTitle
import com.drift.stream.domain.usecase.toHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HistoryRepositoryImpl
    @Inject
    constructor(
        private val progressDao: ProgressDao,
        private val activeProfile: ActiveProfileProvider,
    ) : HistoryRepository {
        override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
            activeProfile.scopedFlow { profileId ->
                progressDao.observeHistoryRows(profileId, limit).map { rows ->
                    val uniqueRows = latestPerTitle(rows)
                    uniqueRows
                        .sortedWith(
                            compareByDescending<ContinueRow> { it.updatedAt }
                                .thenBy { it.metaId },
                        ).map { toHistoryEntry(it) }
                }
            }

        override suspend fun remove(metaId: String) {
            val profileId = activeProfile.requireId()
            progressDao.deleteByMeta(profileId, metaId)
        }

        override suspend fun clear() {
            val profileId = activeProfile.requireId()
            progressDao.deleteAll(profileId)
        }
    }
