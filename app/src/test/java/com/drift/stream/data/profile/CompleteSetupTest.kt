package com.drift.stream.data.profile

import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.DownloadManager
import com.drift.stream.domain.repository.UpdateResult
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompleteSetupTest {
    private fun mockDatabase(profileDao: ProfileDao): NexusDatabase {
        val database = mockk<NexusDatabase>()
        every { database.profileDao() } returns profileDao
        every { database.addonDao() } returns mockk(relaxed = true)
        every { database.profileAddonPrefsDao() } returns mockk(relaxed = true)
        every { database.progressDao() } returns mockk(relaxed = true)
        every { database.libraryDao() } returns mockk(relaxed = true)
        every { database.searchHistoryDao() } returns mockk(relaxed = true)
        every { database.downloadDao() } returns mockk(relaxed = true)
        return database
    }

    private fun TestScope.createRepository(
        database: NexusDatabase,
        activeProfileProvider: ActiveProfileProvider = FakeActiveProfileProvider(1L),
    ): ProfileRepositoryImpl =
        ProfileRepositoryImpl(
            database = database,
            transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T = block()
                },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            activeProfileProvider = activeProfileProvider,
            profileDataStores = mockk(relaxed = true),
            downloadManager = { mockk(relaxed = true) },
        ).apply {
            this.avatarPhotoStore = mockk(relaxed = true)
        }

    @Test
    fun CS1_completeSetup_updatesNameAvatarAndClearsNeedsSetupInOneWrite() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val existing =
                ProfileEntity(
                    id = 1L,
                    name = "Me",
                    avatarColor = 0,
                    avatarEmoji = null,
                    isOwner = true,
                    needsSetup = true,
                    pinHash = null,
                    pinSalt = null,
                    pinIterations = null,
                    createdAt = 1000L,
                    lastUsedAt = 1000L,
                    sortOrder = 0,
                )
            coEvery { profileDao.getById(1L) } returns existing
            coEvery { profileDao.getAll() } returns listOf(existing)

            val slot = slot<ProfileEntity>()
            coEvery { profileDao.update(capture(slot)) } returns Unit

            val repo = createRepository(mockDatabase(profileDao))
            val result = repo.completeSetup(1L, ProfileDraft(name = "Alex", avatarColor = 3, avatarEmoji = "🚀"))

            assertTrue(result is UpdateResult.Updated)
            val updated = (result as UpdateResult.Updated).profile
            assertEquals("Alex", updated.name)
            assertEquals(3, updated.avatarColor)
            assertEquals("🚀", updated.avatarEmoji)
            assertFalse(updated.needsSetup)

            coVerify(exactly = 1) { profileDao.update(any()) }
            assertEquals("Alex", slot.captured.name)
            assertEquals(3, slot.captured.avatarColor)
            assertEquals("🚀", slot.captured.avatarEmoji)
            assertFalse(slot.captured.needsSetup)
        }

    @Test
    fun CS2_completeSetup_unknownId_returnsFailure() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            coEvery { profileDao.getById(99L) } returns null

            val repo = createRepository(mockDatabase(profileDao))
            val result = repo.completeSetup(99L, ProfileDraft(name = "Alex", avatarColor = 3, avatarEmoji = "🚀"))

            assertEquals(UpdateResult.NotFound, result)
            coVerify(exactly = 0) { profileDao.update(any()) }
        }

    @Test
    fun CS3_completeSetup_blankName_isRejected() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val existing =
                ProfileEntity(
                    id = 1L,
                    name = "Me",
                    avatarColor = 0,
                    avatarEmoji = null,
                    isOwner = true,
                    needsSetup = true,
                    pinHash = null,
                    pinSalt = null,
                    pinIterations = null,
                    createdAt = 1000L,
                    lastUsedAt = 1000L,
                    sortOrder = 0,
                )
            coEvery { profileDao.getById(1L) } returns existing
            coEvery { profileDao.getAll() } returns listOf(existing)

            val repo = createRepository(mockDatabase(profileDao))
            val result = repo.completeSetup(1L, ProfileDraft(name = "   ", avatarColor = 3, avatarEmoji = "🚀"))

            assertTrue(result is UpdateResult.InvalidName)
            coVerify(exactly = 0) { profileDao.update(any()) }
        }
}
