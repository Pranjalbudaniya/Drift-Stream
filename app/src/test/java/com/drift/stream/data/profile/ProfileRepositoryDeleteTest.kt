package com.drift.stream.data.profile

import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryDeleteTest {
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
        avatarPhotoStore: AvatarPhotoStore,
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
            this.avatarPhotoStore = avatarPhotoStore
        }

    private fun sampleEntity(
        id: Long,
        isOwner: Boolean = false,
    ): ProfileEntity =
        ProfileEntity(
            id = id,
            name = if (isOwner) "Me" else "Profile$id",
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = false,
            pinHash = null,
            pinSalt = null,
            pinIterations = null,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    @Test
    fun PD1_delete_removesAvatarPhotoFile() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val target = sampleEntity(id = 2L, isOwner = false)

            coEvery { profileDao.getById(2L) } returns target
            coEvery { profileDao.count() } returns 3

            val avatarPhotoStore = mockk<AvatarPhotoStore>(relaxed = true)
            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    avatarPhotoStore = avatarPhotoStore,
                    activeProfileProvider = FakeActiveProfileProvider(1L),
                )

            val result = repository.delete(2L)
            assertEquals(DeleteResult.Deleted, result)

            coVerify(exactly = 1) { avatarPhotoStore.delete(2L) }
            coVerify(exactly = 0) { avatarPhotoStore.delete(1L) }
            coVerify(exactly = 0) { avatarPhotoStore.delete(3L) }
        }

    @Test
    fun PD2_delete_refused_keepsAvatarPhotoFile() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val owner = sampleEntity(id = 1L, isOwner = true)

            coEvery { profileDao.getById(1L) } returns owner
            coEvery { profileDao.count() } returns 2

            val avatarPhotoStore = mockk<AvatarPhotoStore>(relaxed = true)
            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    avatarPhotoStore = avatarPhotoStore,
                    activeProfileProvider = FakeActiveProfileProvider(1L),
                )

            val result = repository.delete(1L)
            assertTrue(result is DeleteResult.RefusedOwner)

            coVerify(exactly = 0) { avatarPhotoStore.delete(any()) }
        }
}
