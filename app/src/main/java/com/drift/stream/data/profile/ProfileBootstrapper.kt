package com.drift.stream.data.profile

import com.drift.stream.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileBootstrapper
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileHolder,
    ) {
        fun start(scope: CoroutineScope) {
            scope.launch {
                profileRepository.ensureDefaultProfile()
                activeProfileHolder.resolve()
            }
        }
    }
