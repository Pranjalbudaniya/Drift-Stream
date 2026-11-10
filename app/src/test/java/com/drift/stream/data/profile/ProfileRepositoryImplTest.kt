package com.drift.stream.data.profile

import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.DownloadDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.ProfileAddonPrefsDao
import com.drift.stream.data.local.db.dao.ProfileDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest {
    private fun mockDatabase(
        profileDao: ProfileDao,
        addonDao: AddonDao = mockk(relaxed = true),
        profileAddonPrefsDao: ProfileAddonPrefsDao = mockk(relaxed = true),
    ): NexusDatabase {
        val database = mockk<NexusDatabase>()
        every { database.profileDao() } returns profileDao
        every { database.addonDao() } returns addonDao
        every { database.profileAddonPrefsDao() } returns profileAddonPrefsDao
        every { database.progressDao() } returns mockk(relaxed = true)
        every { database.libraryDao() } returns mockk(relaxed = true)
        every { database.searchHistoryDao() } returns mockk(relaxed = true)
        every { database.downloadDao() } returns mockk(relaxed = true)
        return database
    }

    private fun TestScope.createRepository(
        database: NexusDatabase,
        transactionRunner: TransactionRunner =
            object : TransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T = block()
            },
        activeProfileProvider: ActiveProfileProvider = FakeActiveProfileProvider(1L),
        profileDataStores: ProfileDataStores = mockk(relaxed = true),
    ): ProfileRepositoryImpl =
        ProfileRepositoryImpl(
            database = database,
            transactionRunner = transactionRunner,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            activeProfileProvider = activeProfileProvider,
            profileDataStores = profileDataStores,
        )

    @Test
    fun PF1_ensureDefaultProfile_createsProfileOneWhenEmpty() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T = block()
                }

            every { profileDao.observeAll() } returns flowOf(emptyList())
            coEvery { profileDao.count() } returns 0
            val insertedSlot = slot<ProfileEntity>()
            coEvery { profileDao.insert(capture(insertedSlot)) } returns 1L

            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    transactionRunner = transactionRunner,
                )

            val profile = repository.ensureDefaultProfile()
            coVerify(exactly = 1) { profileDao.insert(any()) }
            assertEquals(1L, profile.id)
            assertEquals("Me", profile.name)
            assertTrue(profile.isOwner)
            assertTrue(profile.needsSetup)
            assertEquals(1L, insertedSlot.captured.id)
            assertEquals("Me", insertedSlot.captured.name)
            assertTrue(insertedSlot.captured.isOwner)
            assertTrue(insertedSlot.captured.needsSetup)
        }

    @Test
    fun PF2_ensureDefaultProfile_returnsLowestIdWhenPopulated() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T = block()
                }

            val profile1 =
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
                )
            every { profileDao.observeAll() } returns flowOf(listOf(profile1))
            coEvery { profileDao.count() } returns 2
            coEvery { profileDao.getLowestId() } returns profile1

            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    transactionRunner = transactionRunner,
                )

            val result = repository.ensureDefaultProfile()
            coVerify(exactly = 0) { profileDao.insert(any()) }
            assertEquals(1L, result.id)
            assertEquals("Me", result.name)
        }

    @Test
    fun PF3_create_validName_insertsNonOwnerWithNextSortOrder() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T = block()
                }

            val existing =
                listOf(
                    ProfileEntity(1L, "Me", 0, null, true, false, null, null, null, 1000L, 1000L, 0),
                )
            every { profileDao.observeAll() } returns flowOf(existing)
            coEvery { profileDao.getAll() } returns existing
            coEvery { profileDao.maxSortOrder() } returns 0
            val insertedSlot = slot<ProfileEntity>()
            coEvery { profileDao.insert(capture(insertedSlot)) } returns 2L

            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    transactionRunner = transactionRunner,
                )

            val result = repository.create(name = "Guest", avatarColor = 2, avatarEmoji = "🍿")
            assertTrue(result is CreateResult.Created)
            val created = (result as CreateResult.Created).profile
            assertEquals(2L, created.id)
            assertEquals("Guest", created.name)
            assertFalse(created.isOwner)
            assertFalse(created.needsSetup)
            assertEquals(1, created.sortOrder)
            assertEquals(1, insertedSlot.captured.sortOrder)
            assertFalse(insertedSlot.captured.isOwner)
            assertFalse(insertedSlot.captured.needsSetup)
        }

    @Test
    fun PF4_delete_refusesOwnerAndLastProfile() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val addonDao = mockk<AddonDao>(relaxed = true)
            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T = block()
                }

            val owner = ProfileEntity(1L, "Me", 0, null, true, false, null, null, null, 1000L, 1000L, 0)
            val nonOwnerOnly = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)

            every { profileDao.observeAll() } returns flowOf(listOf(owner, nonOwnerOnly))
            coEvery { profileDao.getById(1L) } returns owner
            coEvery { profileDao.getById(2L) } returns nonOwnerOnly
            coEvery { profileDao.count() } returns 1

            val repository =
                createRepository(
                    database = mockDatabase(profileDao = profileDao, addonDao = addonDao),
                    transactionRunner = transactionRunner,
                )

            val ownerResult = repository.delete(1L)
            assertEquals(DeleteResult.RefusedOwner, ownerResult)

            val lastProfileResult = repository.delete(2L)
            assertEquals(DeleteResult.RefusedLast, lastProfileResult)

            coVerify(exactly = 0) { profileDao.deleteById(any()) }
            coVerify(exactly = 0) { addonDao.deleteAllForProfile(any()) }
        }

    @Test
    fun PF5_delete_removesChildRowsAndProfileInOneTransaction() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val addonDao = mockk<AddonDao>(relaxed = true)
            val progressDao = mockk<ProgressDao>(relaxed = true)
            val libraryDao = mockk<LibraryDao>(relaxed = true)
            val searchHistoryDao = mockk<SearchHistoryDao>(relaxed = true)
            val downloadDao = mockk<DownloadDao>(relaxed = true)
            val profileAddonPrefsDao = mockk<ProfileAddonPrefsDao>(relaxed = true)

            var transactionRuns = 0
            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T {
                        transactionRuns++
                        return block()
                    }
                }

            val database = mockk<NexusDatabase>()
            every { database.profileDao() } returns profileDao
            every { database.addonDao() } returns addonDao
            every { database.profileAddonPrefsDao() } returns profileAddonPrefsDao
            every { database.progressDao() } returns progressDao
            every { database.libraryDao() } returns libraryDao
            every { database.searchHistoryDao() } returns searchHistoryDao
            every { database.downloadDao() } returns downloadDao

            val nonOwner = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)
            every { profileDao.observeAll() } returns flowOf(listOf(nonOwner))
            coEvery { profileDao.getById(2L) } returns nonOwner
            coEvery { profileDao.count() } returns 2

            val repository =
                createRepository(
                    database = database,
                    transactionRunner = transactionRunner,
                )

            val result = repository.delete(2L)
            assertEquals(DeleteResult.Deleted, result)
            assertEquals(1, transactionRuns)

            coVerify(exactly = 1) { profileAddonPrefsDao.deleteAllForProfile(2L) }
            coVerify(exactly = 1) { addonDao.deleteAllForProfile(2L) }
            coVerify(exactly = 1) { progressDao.deleteAllForProfile(2L) }
            coVerify(exactly = 1) { libraryDao.deleteAllForProfile(2L) }
            coVerify(exactly = 1) { searchHistoryDao.deleteAllForProfile(2L) }
            coVerify(exactly = 1) { downloadDao.deleteAllForProfile(2L) }
            coVerify(exactly = 1) { profileDao.deleteById(2L) }
        }

    @Test
    fun DEL1_deleteActiveProfile_isRefused() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val nonOwner = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)
            coEvery { profileDao.getById(2L) } returns nonOwner
            coEvery { profileDao.count() } returns 2

            val activeProfileProvider = FakeActiveProfileProvider(2L)
            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    activeProfileProvider = activeProfileProvider,
                )

            val result = repository.delete(2L)
            assertEquals(DeleteResult.RefusedActive, result)
            coVerify(exactly = 0) { profileDao.deleteById(any()) }
        }

    @Test
    fun DEL2_deleteProfile_removesItsDataStoreFiles() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val nonOwner = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)
            coEvery { profileDao.getById(2L) } returns nonOwner
            coEvery { profileDao.count() } returns 2

            val profileDataStores = mockk<ProfileDataStores>(relaxed = true)
            val activeProfileProvider = FakeActiveProfileProvider(1L)
            var transactionRan = false
            var storesDeleteCalledAfterTx = false

            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T {
                        transactionRan = true
                        return block()
                    }
                }

            every { profileDataStores.delete(2L) } answers {
                if (transactionRan) {
                    storesDeleteCalledAfterTx = true
                }
            }

            val repository =
                createRepository(
                    database = mockDatabase(profileDao),
                    transactionRunner = transactionRunner,
                    activeProfileProvider = activeProfileProvider,
                    profileDataStores = profileDataStores,
                )

            val result = repository.delete(2L)
            assertEquals(DeleteResult.Deleted, result)
            assertTrue(storesDeleteCalledAfterTx)
            verify(exactly = 1) { profileDataStores.delete(2L) }
        }

    @Test
    fun SD1_deleteProfile_removesPrefRowsInBothDirections() =
        runTest {
            val profileDao = mockk<ProfileDao>(relaxed = true)
            val nonOwner = ProfileEntity(2L, "Guest", 1, null, false, false, null, null, null, 1000L, 1000L, 1)
            coEvery { profileDao.getById(2L) } returns nonOwner
            coEvery { profileDao.count() } returns 2

            val profileAddonPrefsDao = mockk<ProfileAddonPrefsDao>(relaxed = true)
            val repository =
                createRepository(
                    database = mockDatabase(profileDao = profileDao, profileAddonPrefsDao = profileAddonPrefsDao),
                    activeProfileProvider = FakeActiveProfileProvider(1L),
                )

            val result = repository.delete(2L)
            assertEquals(DeleteResult.Deleted, result)
            coVerify(exactly = 1) { profileAddonPrefsDao.deleteAllForProfile(2L) }
        }
}
