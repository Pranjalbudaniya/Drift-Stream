package com.drift.stream.domain.usecase

import com.drift.stream.domain.model.AddonOwner
import com.drift.stream.domain.model.CINEMETA_URL
import com.drift.stream.domain.model.Outcome
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.AddonRepository
import com.drift.stream.domain.repository.AppStateRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnsureDefaultAddons
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val appStateRepository: AppStateRepository,
        private val activeProfileProvider: ActiveProfileProvider,
    ) {
        private val mutex = Mutex()

        suspend operator fun invoke(): Outcome<Unit> =
            mutex.withLock {
                val profileId = activeProfileProvider.activeProfileId.filterNotNull().first()
                val seededProfiles = appStateRepository.defaultAddonsSeededProfiles.first()
                val legacySeeded = appStateRepository.defaultAddonsSeeded.first()

                val isSeeded = profileId in seededProfiles || (profileId == 1L && legacySeeded)
                if (isSeeded) {
                    return@withLock Outcome.Success(Unit)
                }

                val currentAddons = addonRepository.observeAll().first().filter { it.owner is AddonOwner.Own }
                if (currentAddons.isNotEmpty()) {
                    appStateRepository.markDefaultAddonsSeeded(profileId)
                    return@withLock Outcome.Success(Unit)
                }

                when (val result = addonRepository.install(CINEMETA_URL)) {
                    is Outcome.Success -> {
                        appStateRepository.markDefaultAddonsSeeded(profileId)
                        Outcome.Success(Unit)
                    }

                    is Outcome.Failure -> {
                        Outcome.Failure(result.error)
                    }
                }
            }
    }
