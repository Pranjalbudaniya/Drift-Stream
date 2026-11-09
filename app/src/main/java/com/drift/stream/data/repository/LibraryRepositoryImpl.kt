package com.drift.stream.data.repository

import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.profile.requireId
import com.drift.stream.data.profile.scopedFlow
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.model.ListStatus
import com.drift.stream.domain.model.MetaPreview
import com.drift.stream.domain.model.ProgressTarget
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.LibraryItem
import com.drift.stream.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LibraryRepositoryImpl(
    private val libraryDao: LibraryDao,
    private val activeProfile: ActiveProfileProvider,
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob()),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LibraryRepository {
    @Inject
    constructor(
        libraryDao: LibraryDao,
        activeProfile: ActiveProfileProvider,
        transactionRunner: TransactionRunner,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        libraryDao = libraryDao,
        activeProfile = activeProfile,
        transactionRunner = transactionRunner,
        dispatcher = dispatcher,
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        clock = { System.currentTimeMillis() },
    )

    private val sharedStatuses: StateFlow<Map<String, ListStatus>> =
        activeProfile
            .scopedFlow { profileId ->
                libraryDao.observeListStatuses(profileId).map { list ->
                    list.associate { tuple ->
                        val status =
                            runCatching {
                                ListStatus.valueOf(tuple.listStatus)
                            }.getOrDefault(ListStatus.PLAN_TO_WATCH)
                        tuple.metaId to status
                    }
                }
            }.distinctUntilChanged()
            .flowOn(dispatcher)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    override fun observeListStatuses(): Flow<Map<String, ListStatus>> = sharedStatuses

    override suspend fun setStatus(
        item: MetaPreview,
        status: ListStatus?,
    ) {
        val profileId = activeProfile.requireId()
        withContext(dispatcher) {
            transactionRunner.run {
                val existing = libraryDao.get(profileId, item.id)
                if (status == null) {
                    if (existing != null && existing.inWatchlist) {
                        libraryDao.upsert(existing.copy(inWatchlist = false))
                    }
                } else {
                    val entity =
                        if (existing != null) {
                            existing.copy(
                                type = item.type,
                                name = item.name,
                                poster = item.poster,
                                background = item.background,
                                inWatchlist = true,
                                listStatus = status.name,
                            )
                        } else {
                            LibraryItemEntity(
                                metaId = item.id,
                                type = item.type,
                                name = item.name,
                                poster = item.poster,
                                background = item.background,
                                inWatchlist = true,
                                addedAt = clock(),
                                listStatus = status.name,
                                profileId = profileId,
                            )
                        }
                    libraryDao.upsert(entity)
                }
            }
        }
    }

    override suspend fun ensureSnapshot(target: ProgressTarget) {
        val profileId = activeProfile.requireId()
        withContext(dispatcher) {
            transactionRunner.run {
                val existing = libraryDao.get(profileId, target.metaId)
                if (existing == null) {
                    libraryDao.upsert(
                        LibraryItemEntity(
                            metaId = target.metaId,
                            type = target.type,
                            name = target.name,
                            poster = target.poster,
                            background = target.background,
                            inWatchlist = false,
                            addedAt = clock(),
                            profileId = profileId,
                        ),
                    )
                } else {
                    val updatedName = if (target.name.isNotBlank()) target.name else existing.name
                    val updatedPoster = if (!target.poster.isNullOrBlank()) target.poster else existing.poster
                    val updatedBackground =
                        if (!target.background.isNullOrBlank()) target.background else existing.background
                    if (updatedName != existing.name ||
                        updatedPoster != existing.poster ||
                        updatedBackground != existing.background
                    ) {
                        libraryDao.upsert(
                            existing.copy(
                                name = updatedName,
                                poster = updatedPoster,
                                background = updatedBackground,
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun getItem(metaId: String): LibraryItem? {
        val profileId = activeProfile.requireId()
        return withContext(dispatcher) {
            val entity = libraryDao.get(profileId, metaId) ?: return@withContext null
            val status =
                runCatching {
                    ListStatus.valueOf(entity.listStatus)
                }.getOrDefault(ListStatus.PLAN_TO_WATCH)
            LibraryItem(
                metaId = entity.metaId,
                inWatchlist = entity.inWatchlist,
                status = status,
            )
        }
    }

    override suspend fun setStatusById(
        metaId: String,
        status: ListStatus,
    ) {
        val profileId = activeProfile.requireId()
        withContext(dispatcher) {
            transactionRunner.run {
                val existing = libraryDao.get(profileId, metaId) ?: return@run
                libraryDao.upsert(
                    existing.copy(
                        inWatchlist = true,
                        listStatus = status.name,
                    ),
                )
            }
        }
    }
}
