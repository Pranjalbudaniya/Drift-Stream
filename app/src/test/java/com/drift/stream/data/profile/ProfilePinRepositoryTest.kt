package com.drift.stream.data.profile

import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.data.local.db.entity.ProfilePinEntity
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.DownloadManager
import com.drift.stream.domain.repository.SetPinResult
import com.drift.stream.domain.repository.VerifyResult
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePinRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class CountingPinHasher(
        defaultIterations: Int = 1_000,
        dispatcher: CoroutineDispatcher,
    ) : PinHasher(defaultIterations, dispatcher) {
        var verifyCallCount = 0

        override suspend fun verify(
            pin: String,
            salt: String,
            iterations: Int,
            expectedHash: String,
        ): Boolean {
            verifyCallCount++
            return super.verify(pin, salt, iterations, expectedHash)
        }
    }

    private class MutableClock(
        private var currentMillis: Long,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)

        override fun millis(): Long = currentMillis

        fun advance(ms: Long) {
            currentMillis += ms
        }
    }

    private class FakeProfileDao : ProfileDao {
        private val profiles = mutableMapOf<Long, ProfileEntity>()
        private val profilesFlow = MutableStateFlow<List<ProfileEntity>>(emptyList())

        fun addProfile(entity: ProfileEntity) {
            synchronized(this) {
                profiles[entity.id] = entity
                profilesFlow.value = profiles.values.sortedBy { it.sortOrder }
            }
        }

        override fun observeAll(): Flow<List<ProfileEntity>> = profilesFlow

        override suspend fun getAll(): List<ProfileEntity> =
            synchronized(this) {
                profiles.values.sortedBy { it.sortOrder }
            }

        override suspend fun getById(id: Long): ProfileEntity? =
            synchronized(this) {
                profiles[id]
            }

        override suspend fun getLowestId(): ProfileEntity? =
            synchronized(this) {
                profiles.values.minByOrNull { it.id }
            }

        override suspend fun getOwner(): ProfileEntity? =
            synchronized(this) {
                profiles.values.firstOrNull { it.isOwner }
            }

        override suspend fun count(): Int =
            synchronized(this) {
                profiles.size
            }

        override suspend fun maxSortOrder(): Int? =
            synchronized(this) {
                profiles.values.maxOfOrNull { it.sortOrder }
            }

        override suspend fun insert(profile: ProfileEntity): Long =
            synchronized(this) {
                val id = if (profile.id == 0L) (profiles.keys.maxOrNull() ?: 0L) + 1L else profile.id
                val entity = profile.copy(id = id)
                profiles[id] = entity
                profilesFlow.value = profiles.values.sortedBy { it.sortOrder }
                id
            }

        override suspend fun update(profile: ProfileEntity) {
            synchronized(this) {
                profiles[profile.id] = profile
                profilesFlow.value = profiles.values.sortedBy { it.sortOrder }
            }
        }

        override suspend fun deleteById(id: Long) {
            synchronized(this) {
                profiles.remove(id)
                profilesFlow.value = profiles.values.sortedBy { it.sortOrder }
            }
        }

        override suspend fun updateAvatarPhotoStamp(
            id: Long,
            stamp: Long?,
        ) {
            synchronized(this) {
                profiles[id]?.let {
                    profiles[id] = it.copy(avatarPhotoStamp = stamp)
                }
            }
        }

        override suspend fun getPinRow(id: Long): ProfilePinEntity? =
            synchronized(this) {
                profiles[id]?.let {
                    ProfilePinEntity(
                        pinHash = it.pinHash,
                        pinSalt = it.pinSalt,
                        pinIterations = it.pinIterations,
                        pinFailedAttempts = it.pinFailedAttempts,
                        pinLockedUntil = it.pinLockedUntil,
                    )
                }
            }

        override suspend fun setPin(
            id: Long,
            hash: String,
            salt: String,
            iterations: Int,
        ) {
            synchronized(this) {
                profiles[id]?.let {
                    profiles[id] =
                        it.copy(
                            pinHash = hash,
                            pinSalt = salt,
                            pinIterations = iterations,
                            pinFailedAttempts = null,
                            pinLockedUntil = null,
                        )
                }
            }
        }

        override suspend fun clearPin(id: Long) {
            synchronized(this) {
                profiles[id]?.let {
                    profiles[id] =
                        it.copy(
                            pinHash = null,
                            pinSalt = null,
                            pinIterations = null,
                            pinFailedAttempts = null,
                            pinLockedUntil = null,
                        )
                }
            }
        }

        override suspend fun recordPinFailure(
            id: Long,
            attempts: Int,
            lockedUntil: Long?,
        ) {
            synchronized(this) {
                profiles[id]?.let {
                    profiles[id] =
                        it.copy(
                            pinFailedAttempts = attempts,
                            pinLockedUntil = lockedUntil,
                        )
                }
            }
        }

        override suspend fun resetPinFailures(id: Long) {
            synchronized(this) {
                profiles[id]?.let {
                    profiles[id] =
                        it.copy(
                            pinFailedAttempts = null,
                            pinLockedUntil = null,
                        )
                }
            }
        }
    }

    private lateinit var dao: FakeProfileDao
    private lateinit var hasher: CountingPinHasher
    private lateinit var clock: MutableClock
    private lateinit var repository: ProfilePinRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeProfileDao()
        clock = MutableClock(1_000_000L)
        hasher = CountingPinHasher(defaultIterations = 1_000, dispatcher = testDispatcher)
        repository =
            ProfilePinRepositoryImpl(
                profileDao = dao,
                pinHasher = hasher,
                clock = clock,
                ioDispatcher = testDispatcher,
            )

        dao.addProfile(
            ProfileEntity(
                id = 1L,
                name = "Me",
                avatarColor = 0,
                avatarEmoji = null,
                isOwner = true,
                needsSetup = false,
                pinHash = null,
                pinSalt = null,
                pinIterations = null,
                createdAt = 1000L,
                lastUsedAt = 1000L,
                sortOrder = 0,
            ),
        )
    }

    @Test
    fun PV1_setPin_storesHashSaltIterations_andLocksProfile() =
        testScope.runTest {
            val result = repository.setPin(1L, "1234")
            assertEquals(SetPinResult.Set, result)

            val row = dao.getPinRow(1L)
            assertNotNull(row)
            assertNotNull(row?.pinHash)
            assertNotNull(row?.pinSalt)
            assertEquals(1_000, row?.pinIterations)

            val profile = dao.getById(1L)?.toDomain()
            assertTrue(profile?.isLocked == true)
        }

    @Test
    fun PV2_setPin_invalidPin_isRefused() =
        testScope.runTest {
            val result = repository.setPin(1L, "12")
            assertEquals(SetPinResult.InvalidPin, result)

            val row = dao.getPinRow(1L)
            assertNull(row?.pinHash)
            assertFalse(dao.getById(1L)?.toDomain()?.isLocked == true)
        }

    @Test
    fun PV3_verify_correct_resetsCounters() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            repository.verify(1L, "0000")
            repository.verify(1L, "1111")
            assertEquals(2, dao.getPinRow(1L)?.pinFailedAttempts)

            val result = repository.verify(1L, "1234")
            assertEquals(VerifyResult.Correct, result)
            assertNull(dao.getPinRow(1L)?.pinFailedAttempts)
        }

    @Test
    fun PV4_verify_wrong_reportsTriesLeft() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            val r1 = repository.verify(1L, "0000")
            assertEquals(VerifyResult.Wrong(4), r1)

            val r2 = repository.verify(1L, "0000")
            assertEquals(VerifyResult.Wrong(3), r2)
        }

    @Test
    fun PV5_verify_fifthWrong_locks30s() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            repeat(4) { repository.verify(1L, "0000") }

            val r5 = repository.verify(1L, "0000")
            assertEquals(VerifyResult.Locked(30_000L), r5)
            assertEquals(clock.millis() + 30_000L, dao.getPinRow(1L)?.pinLockedUntil)
        }

    @Test
    fun PV6_verify_whileLocked_doesNotHash() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            repeat(5) { repository.verify(1L, "0000") }

            val callsBefore = hasher.verifyCallCount
            val result = repository.verify(1L, "1234")

            assertEquals(VerifyResult.Locked(30_000L), result)
            assertEquals(callsBefore, hasher.verifyCallCount)
        }

    @Test
    fun PV7_verify_afterLockExpires_wrongIsAttemptSix_noLock() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            repeat(5) { repository.verify(1L, "0000") }

            clock.advance(31_000L)
            val result = repository.verify(1L, "0000")
            assertEquals(VerifyResult.Wrong(4), result)
            assertEquals(6, dao.getPinRow(1L)?.pinFailedAttempts)
        }

    @Test
    fun PV8_verify_success_afterFailures_resetsToZero() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            repeat(3) { repository.verify(1L, "0000") }
            val ok = repository.verify(1L, "1234")
            assertEquals(VerifyResult.Correct, ok)

            val wrong = repository.verify(1L, "0000")
            assertEquals(VerifyResult.Wrong(4), wrong)
        }

    @Test
    fun PV9_removePin_clearsAllPinColumns() =
        testScope.runTest {
            repository.setPin(1L, "1234")
            repository.removePin(1L)

            val row = dao.getPinRow(1L)
            assertNotNull(row)
            assertNull(row?.pinHash)
            assertNull(row?.pinSalt)
            assertNull(row?.pinIterations)
            assertNull(row?.pinFailedAttempts)
            assertNull(row?.pinLockedUntil)
            assertFalse(dao.getById(1L)?.toDomain()?.isLocked == true)
        }

    @Test
    fun PV10_verify_noPin_returnsNoPin() =
        testScope.runTest {
            val result = repository.verify(1L, "1234")
            assertEquals(VerifyResult.NoPin, result)

            val unknown = repository.verify(999L, "1234")
            assertEquals(VerifyResult.NotFound, unknown)
        }

    @Test
    fun PV11_update_doesNotOverwritePinColumns() =
        testScope.runTest {
            val db = mockk<NexusDatabase>(relaxed = true)
            val profileRepo =
                ProfileRepositoryImpl(
                    database = db,
                    transactionRunner =
                        object : TransactionRunner {
                            override suspend fun <T> run(block: suspend () -> T): T = block()
                        },
                    ioDispatcher = testDispatcher,
                    activeProfileProvider = mockk<ActiveProfileProvider>(relaxed = true),
                    profileFilesCleaner = mockk<ProfileFilesCleaner>(relaxed = true),
                    downloadManager = { mockk<DownloadManager>(relaxed = true) },
                )

            // Inject the fake DAO via reflection or private field
            val daoField = ProfileRepositoryImpl::class.java.getDeclaredField("profileDao")
            daoField.isAccessible = true
            daoField.set(profileRepo, dao)

            val oldProfile = dao.getById(1L)!!.toDomain()
            repository.setPin(1L, "1234")

            profileRepo.update(oldProfile.copy(name = "Updated Name"))

            assertEquals("Updated Name", dao.getById(1L)?.name)
            val verify = repository.verify(1L, "1234")
            assertEquals(VerifyResult.Correct, verify)
        }

    @Test
    fun PV12_verify_parallelWrongGuesses_areCountedOneByOne() =
        testScope.runTest {
            repository.setPin(1L, "1234")

            val job1 = async { repository.verify(1L, "0000") }
            val job2 = async { repository.verify(1L, "0000") }
            awaitAll(job1, job2)

            assertEquals(2, dao.getPinRow(1L)?.pinFailedAttempts)
        }
}
