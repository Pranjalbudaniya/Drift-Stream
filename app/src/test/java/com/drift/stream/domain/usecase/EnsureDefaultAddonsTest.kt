package com.drift.stream.domain.usecase

import com.drift.stream.domain.model.Addon
import com.drift.stream.domain.model.AddonManifest
import com.drift.stream.domain.model.AddonOwner
import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.AppError
import com.drift.stream.domain.model.CINEMETA_URL
import com.drift.stream.domain.model.ManifestHints
import com.drift.stream.domain.model.Outcome
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.AddonRepository
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsureDefaultAddonsTest {
    private class FakeAppStateRepository(
        initialSeeded: Boolean = false,
        initialSeededProfiles: Set<Long> = emptySet(),
    ) : AppStateRepository {
        private val seededState = MutableStateFlow(initialSeeded)
        override val defaultAddonsSeeded: Flow<Boolean> = seededState

        override suspend fun markDefaultAddonsSeeded() {
            seededState.value = true
        }

        private val seededProfilesState = MutableStateFlow(initialSeededProfiles)
        override val defaultAddonsSeededProfiles: Flow<Set<Long>> = seededProfilesState

        override suspend fun markDefaultAddonsSeeded(profileId: Long) {
            seededProfilesState.value = seededProfilesState.value + profileId
        }

        private val lastActiveProfileState = MutableStateFlow<Long?>(null)
        override val lastActiveProfileId: Flow<Long?> = lastActiveProfileState

        override suspend fun setLastActiveProfileId(id: Long) {
            lastActiveProfileState.value = id
        }
    }

    private val sampleAddon =
        Addon(
            transportUrl = CINEMETA_URL,
            manifest =
                AddonManifest(
                    id = "org.stremio.cinemeta",
                    version = "3.0.14",
                    name = "Cinemeta",
                    description = "Official",
                    logo = null,
                    background = null,
                    types = listOf("movie"),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = false,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 1000L,
            owner = AddonOwner.Own,
        )

    private fun createUseCase(
        addonRepo: AddonRepository,
        appStateRepo: AppStateRepository,
        activeProfileProvider: ActiveProfileProvider = FakeActiveProfileProvider(1L),
    ): EnsureDefaultAddons =
        EnsureDefaultAddons(
            addonRepository = addonRepo,
            appStateRepository = appStateRepo,
            activeProfileProvider = activeProfileProvider,
        )

    @Test
    fun seedsWhenEmptyAndNotFlagged_marksFlagAndReturnsSuccess() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } returns Outcome.Success(sampleAddon)

            val useCase = createUseCase(addonRepo, appStateRepo)
            val result = useCase()

            assertTrue(result is Outcome.Success)
            assertTrue(1L in appStateRepo.defaultAddonsSeededProfiles.first())
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }
        }

    @Test
    fun whenAlreadyFlagged_noSeedAndNoInstall() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = true)
            every { addonRepo.observeAll() } returns flowOf(emptyList())

            val useCase = createUseCase(addonRepo, appStateRepo)
            val result = useCase()

            assertTrue(result is Outcome.Success)
            coVerify(exactly = 0) { addonRepo.install(any()) }
        }

    @Test
    fun whenAddonsAlreadyExist_setsFlagAndDoesNotInstall() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(listOf(sampleAddon))

            val useCase = createUseCase(addonRepo, appStateRepo)
            val result = useCase()

            assertTrue(result is Outcome.Success)
            assertTrue(1L in appStateRepo.defaultAddonsSeededProfiles.first())
            coVerify(exactly = 0) { addonRepo.install(any()) }
        }

    @Test
    fun whenInstallFails_leavesFlagFalseAndReturnsFailure() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } returns Outcome.Failure(AppError.Network)

            val useCase = createUseCase(addonRepo, appStateRepo)
            val result = useCase()

            assertEquals(Outcome.Failure(AppError.Network), result)
            assertFalse(1L in appStateRepo.defaultAddonsSeededProfiles.first())
        }

    @Test
    fun concurrentCalls_installOnlyOnce() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } coAnswers {
                delay(50)
                Outcome.Success(sampleAddon)
            }

            val useCase = createUseCase(addonRepo, appStateRepo)
            val call1 = async { useCase() }
            val call2 = async { useCase() }

            val res1 = call1.await()
            val res2 = call2.await()

            assertTrue(res1 is Outcome.Success)
            assertTrue(res2 is Outcome.Success)
            assertTrue(1L in appStateRepo.defaultAddonsSeededProfiles.first())
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }
        }

    @Test
    fun EN1_ensureDefaultAddons_isPerProfile() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = true) // profile 1 legacy seeded
            val activeProfile = FakeActiveProfileProvider(1L)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } returns Outcome.Success(sampleAddon)

            val useCase = createUseCase(addonRepo, appStateRepo, activeProfile)

            // Profile 1: already seeded via old flag, no install
            val res1 = useCase()
            assertTrue(res1 is Outcome.Success)
            coVerify(exactly = 0) { addonRepo.install(any()) }

            // Profile 2: never seeded, gets Cinemeta
            activeProfile.setActive(2L)
            val res2 = useCase()
            assertTrue(res2 is Outcome.Success)
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }
            assertTrue(2L in appStateRepo.defaultAddonsSeededProfiles.first())
        }

    @Test
    fun EN2_ensureDefaultAddons_doesNotSeedTwiceForTheSameProfile() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            val activeProfile = FakeActiveProfileProvider(2L)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } returns Outcome.Success(sampleAddon)

            val useCase = createUseCase(addonRepo, appStateRepo, activeProfile)

            val res1 = useCase()
            assertTrue(res1 is Outcome.Success)
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }

            val res2 = useCase()
            assertTrue(res2 is Outcome.Success)
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }
        }
}
