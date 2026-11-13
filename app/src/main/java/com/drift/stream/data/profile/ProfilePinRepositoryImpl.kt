package com.drift.stream.data.profile

import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.profile.isValidPin
import com.drift.stream.domain.profile.lockDurationMs
import com.drift.stream.domain.profile.remainingLockMs
import com.drift.stream.domain.profile.triesLeft
import com.drift.stream.domain.repository.ProfilePinRepository
import com.drift.stream.domain.repository.SetPinResult
import com.drift.stream.domain.repository.VerifyResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfilePinRepositoryImpl
    @Inject
    constructor(
        private val profileDao: ProfileDao,
        private val pinHasher: PinHasher,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ProfilePinRepository {
        private val verifyMutex = Mutex()

        override suspend fun setPin(
            profileId: Long,
            pin: String,
        ): SetPinResult =
            withContext(ioDispatcher) {
                if (!isValidPin(pin)) return@withContext SetPinResult.InvalidPin
                profileDao.getById(profileId) ?: return@withContext SetPinResult.NotFound
                val salt = pinHasher.generateSalt()
                val hash = pinHasher.hash(pin, salt, pinHasher.defaultIterations)
                profileDao.setPin(profileId, hash, salt, pinHasher.defaultIterations)
                SetPinResult.Set
            }

        override suspend fun removePin(profileId: Long) =
            withContext(ioDispatcher) {
                profileDao.clearPin(profileId)
            }

        override suspend fun verify(
            profileId: Long,
            pin: String,
        ): VerifyResult =
            verifyMutex.withLock {
                withContext(ioDispatcher) {
                    val row = profileDao.getPinRow(profileId) ?: return@withContext VerifyResult.NotFound
                    val hash = row.pinHash ?: return@withContext VerifyResult.NoPin
                    val salt = row.pinSalt ?: return@withContext VerifyResult.NoPin
                    val iterations = row.pinIterations ?: return@withContext VerifyResult.NoPin

                    val now = clock.millis()
                    val remaining = remainingLockMs(row.pinLockedUntil, now)
                    if (remaining > 0L) {
                        return@withContext VerifyResult.Locked(remaining)
                    }

                    val isCorrect = pinHasher.verify(pin, salt, iterations, hash)
                    if (isCorrect) {
                        profileDao.resetPinFailures(profileId)
                        VerifyResult.Correct
                    } else {
                        val attempts = (row.pinFailedAttempts ?: 0) + 1
                        val lock = lockDurationMs(attempts)
                        val lockedUntil = if (lock > 0L) now + lock else null
                        profileDao.recordPinFailure(profileId, attempts, lockedUntil)
                        if (lock > 0L) {
                            VerifyResult.Locked(lock)
                        } else {
                            VerifyResult.Wrong(triesLeft(attempts))
                        }
                    }
                }
            }

        override suspend fun remainingLock(profileId: Long): Long =
            withContext(ioDispatcher) {
                val row = profileDao.getPinRow(profileId) ?: return@withContext 0L
                remainingLockMs(row.pinLockedUntil, clock.millis())
            }
    }
