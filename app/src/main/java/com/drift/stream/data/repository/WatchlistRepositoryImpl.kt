package com.drift.stream.data.repository

import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.domain.model.ListStatus
import com.drift.stream.domain.model.WatchlistEntry
import com.drift.stream.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WatchlistRepositoryImpl
    @Inject
    constructor(
        private val libraryDao: LibraryDao,
    ) : WatchlistRepository {
        override fun observeWatchlist(): Flow<List<WatchlistEntry>> =
            libraryDao.observeWatchlist().map { entities ->
                entities.map { entity ->
                    val status =
                        runCatching { ListStatus.valueOf(entity.listStatus) }
                            .getOrDefault(ListStatus.PLAN_TO_WATCH)
                    WatchlistEntry(
                        metaId = entity.metaId,
                        type = entity.type,
                        name = entity.name,
                        poster = entity.poster,
                        background = entity.background,
                        status = status,
                        addedAt = entity.addedAt,
                    )
                }
            }
    }
