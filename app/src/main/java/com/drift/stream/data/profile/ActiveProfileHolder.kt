package com.drift.stream.data.profile

import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.AppStateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveProfileHolder
    @Inject
    constructor(
        private val appStateRepository: AppStateRepository,
        private val profileDao: ProfileDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ActiveProfileProvider {
        private val _activeProfileId = MutableStateFlow<Long?>(null)
        override val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

        suspend fun resolve(): Long? =
            withContext(ioDispatcher) {
                val storedId = appStateRepository.lastActiveProfileId.first()
                val targetId =
                    if (storedId != null && profileDao.getById(storedId) != null) {
                        storedId
                    } else {
                        val owner = profileDao.getOwner() ?: profileDao.getLowestId()
                        owner?.id
                    }
                if (targetId != null) {
                    appStateRepository.setLastActiveProfileId(targetId)
                    _activeProfileId.value = targetId
                }
                targetId
            }

        suspend fun setActive(id: Long): Boolean =
            withContext(ioDispatcher) {
                val profile = profileDao.getById(id) ?: return@withContext false
                appStateRepository.setLastActiveProfileId(id)
                _activeProfileId.value = id
                profileDao.update(profile.copy(lastUsedAt = System.currentTimeMillis()))
                true
            }
    }
