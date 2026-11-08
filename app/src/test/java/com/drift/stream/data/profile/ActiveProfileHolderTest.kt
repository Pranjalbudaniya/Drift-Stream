package com.drift.stream.data.profile

import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.domain.repository.AppStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveProfileHolderTest {
    @Test
    fun AH1_resolve_usesStoredIdWhenItExists() =
        runTest {
            val appStateRepository = mockk<AppStateRepository>(relaxed = true)
            val profileDao = mockk<ProfileDao>()

            val profile2 = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)
            coEvery { appStateRepository.lastActiveProfileId } returns flowOf(2L)
            coEvery { profileDao.getById(2L) } returns profile2

            val holder =
                ActiveProfileHolder(
                    appStateRepository = appStateRepository,
                    profileDao = profileDao,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val resolved = holder.resolve()
            assertEquals(2L, resolved)
            assertEquals(2L, holder.activeProfileId.value)
            coVerify { appStateRepository.setLastActiveProfileId(2L) }
        }

    @Test
    fun AH2_resolve_fallsBackToOwnerWhenStoredIdIsGone() =
        runTest {
            val appStateRepository = mockk<AppStateRepository>(relaxed = true)
            val profileDao = mockk<ProfileDao>()

            val owner = ProfileEntity(1L, "Me", 0, null, true, false, null, null, null, 1000L, 1000L, 0)
            coEvery { appStateRepository.lastActiveProfileId } returns flowOf(999L)
            coEvery { profileDao.getById(999L) } returns null
            coEvery { profileDao.getOwner() } returns owner

            val holder =
                ActiveProfileHolder(
                    appStateRepository = appStateRepository,
                    profileDao = profileDao,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val resolved = holder.resolve()
            assertEquals(1L, resolved)
            assertEquals(1L, holder.activeProfileId.value)
            coVerify(exactly = 1) { appStateRepository.setLastActiveProfileId(1L) }
        }

    @Test
    fun AH3_setActive_persistsAndEmits() =
        runTest {
            val appStateRepository = mockk<AppStateRepository>(relaxed = true)
            val profileDao = mockk<ProfileDao>(relaxed = true)

            val profile2 = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)
            coEvery { profileDao.getById(2L) } returns profile2

            val holder =
                ActiveProfileHolder(
                    appStateRepository = appStateRepository,
                    profileDao = profileDao,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val success = holder.setActive(2L)
            assertTrue(success)
            assertEquals(2L, holder.activeProfileId.value)
            coVerify(exactly = 1) { appStateRepository.setLastActiveProfileId(2L) }
            coVerify(exactly = 1) { profileDao.update(match { it.id == 2L }) }
        }

    @Test
    fun AH4_setActive_unknownId_isRejectedWithoutChange() =
        runTest {
            val appStateRepository = mockk<AppStateRepository>(relaxed = true)
            val profileDao = mockk<ProfileDao>()

            coEvery { profileDao.getById(999L) } returns null

            val holder =
                ActiveProfileHolder(
                    appStateRepository = appStateRepository,
                    profileDao = profileDao,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val success = holder.setActive(999L)
            assertFalse(success)
            assertEquals(null, holder.activeProfileId.value)
            coVerify(exactly = 0) { appStateRepository.setLastActiveProfileId(any()) }
            coVerify(exactly = 0) { profileDao.update(any()) }
        }
}
