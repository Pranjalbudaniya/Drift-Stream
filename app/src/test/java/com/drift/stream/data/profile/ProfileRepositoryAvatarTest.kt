package com.drift.stream.data.profile

import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DownloadManager
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryAvatarTest {
    private fun mockDatabase(profileDao: ProfileDao): NexusDatabase {
        val database = mockk<NexusDatabase>()
        every { profileDao.observeAll() } returns kotlinx.coroutines.flow.flowOf(emptyList())
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
        downloadManager: DownloadManager = mockk(relaxed = true),
    ): ProfileRepositoryImpl =
        ProfileRepositoryImpl(
            database = database,
            transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T = block()
                },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            activeProfileProvider = activeProfileProvider,
            profileFilesCleaner = ProfileFilesCleaner(mockk(relaxed = true), mockk(relaxed = true)),
            downloadManager = { downloadManager },
        )

    @Test
    fun PR1_create_withCustomColor_roundTrips() =
        runTest {
            val profileDao = mockk<ProfileDao>()
            coEvery { profileDao.getAll() } returns emptyList()
            coEvery { profileDao.maxSortOrder() } returns 0
            val slot = slot<ProfileEntity>()
            coEvery { profileDao.insert(capture(slot)) } returns 42L

            val repo = createRepository(mockDatabase(profileDao))
            val result =
                repo.create(
                    ProfileDraft(
                        name = "Custom",
                        avatarColor = 1,
                        avatarEmoji = null,
                        avatarCustomColor = 0xFF112233.toInt(),
                        avatarPhotoStamp = null,
                    ),
                )

            assertTrue(result is CreateResult.Created)
            val created = (result as CreateResult.Created).profile
            assertEquals(42L, created.id)
            assertEquals("Custom", created.name)
            assertEquals(0xFF112233.toInt(), created.avatarCustomColor)
            assertEquals(0xFF112233.toInt(), slot.captured.avatarCustomColor)
        }

    @Test
    fun PR2_setAvatarPhoto_stampAndNull_roundTrips() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val repo = createRepository(mockDatabase(profileDao))

            repo.setAvatarPhoto(profileId = 1L, stamp = 1700000000L)
            coVerify(exactly = 1) { profileDao.updateAvatarPhotoStamp(1L, 1700000000L) }

            repo.setAvatarPhoto(profileId = 1L, stamp = null)
            coVerify(exactly = 1) { profileDao.updateAvatarPhotoStamp(1L, null) }
        }
}
