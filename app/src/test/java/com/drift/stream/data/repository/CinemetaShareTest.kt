package com.drift.stream.data.repository

import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.FakeAddonDao
import com.drift.stream.data.local.db.dao.FakeProfileAddonPrefsDao
import com.drift.stream.data.remote.addon.AddonRemoteDataSource
import com.drift.stream.data.remote.addon.FetchedManifest
import com.drift.stream.di.NetworkModule
import com.drift.stream.domain.model.AddonManifest
import com.drift.stream.domain.model.AppError
import com.drift.stream.domain.model.CINEMETA_URL
import com.drift.stream.domain.model.ManifestHints
import com.drift.stream.domain.model.Outcome
import com.drift.stream.domain.model.ResourceSpec
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.ContentPrefsRepository
import com.drift.stream.domain.usecase.EnsureDefaultAddons
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class CinemetaFakeAppStateRepo(
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

    private val alwaysShowPickerState = MutableStateFlow(false)
    override val alwaysShowPicker: Flow<Boolean> = alwaysShowPickerState

    override suspend fun setAlwaysShowPicker(alwaysShow: Boolean) {
        alwaysShowPickerState.value = alwaysShow
    }
}

private class CinemetaFakeContentPrefsRepo(
    initialFamilyMode: Boolean = true,
) : ContentPrefsRepository {
    private val familyModeFlow = MutableStateFlow(initialFamilyMode)
    override val familyMode: Flow<Boolean> = familyModeFlow

    override suspend fun setFamilyMode(enabled: Boolean) {
        familyModeFlow.value = enabled
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CinemetaShareTest {
    private val fakeDao = FakeAddonDao()
    private val fakePrefsDao = FakeProfileAddonPrefsDao(fakeDao)
    private val remoteDataSource = mockk<AddonRemoteDataSource>()
    private val contentPrefs = CinemetaFakeContentPrefsRepo()
    private val activeProfile = FakeActiveProfileProvider(1L)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val transactionRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private lateinit var repository: AddonRepositoryImpl

    private fun sampleManifest(
        id: String = "org.stremio.cinemeta",
        name: String = "Cinemeta",
    ) = AddonManifest(
        id = id,
        version = "3.0.14",
        name = name,
        description = "Test description",
        logo = null,
        background = null,
        types = listOf("movie"),
        idPrefixes = null,
        resources = listOf(ResourceSpec("catalog", null, null)),
        catalogs = emptyList(),
        behaviorHints =
            ManifestHints(
                adult = false,
                p2p = false,
                configurable = false,
                configurationRequired = false,
            ),
    )

    private fun sampleRawJson(
        id: String = "org.stremio.cinemeta",
        name: String = "Cinemeta",
    ) = """{"id":"$id","version":"3.0.14","name":"$name","resources":["catalog"],"types":["movie"]}"""

    @Before
    fun setUp() {
        repository =
            AddonRepositoryImpl(
                addonDao = fakeDao,
                profileAddonPrefsDao = fakePrefsDao,
                remoteDataSource = remoteDataSource,
                json = NetworkModule.provideJson(),
                transactionRunner = transactionRunner,
                contentPrefsRepository = contentPrefs,
                clock = { 1000L },
                dispatcher = testDispatcher,
                activeProfile = activeProfile,
            )
    }

    @Test
    fun CM1_defaultAddonInstalledByTheDefaultPath_canBeShared() =
        runTest {
            val cinemetaManifest = sampleManifest("org.stremio.cinemeta", "Cinemeta")
            val cinemetaRaw = sampleRawJson("org.stremio.cinemeta", "Cinemeta")
            coEvery { remoteDataSource.manifestWithRaw("https://v3-cinemeta.strem.io/") } returns
                Outcome.Success(FetchedManifest(cinemetaManifest, cinemetaRaw))

            val installOutcome = repository.install(CINEMETA_URL)
            assertTrue(installOutcome is Outcome.Success)
            val installed = (installOutcome as Outcome.Success).value
            val setSharedOutcome = repository.setShared(installed.transportUrl, true)
            assertEquals(Outcome.Success(Unit), setSharedOutcome)

            val addons = repository.observeAll().first()
            assertEquals(1, addons.size)
            assertTrue(addons[0].shared)
        }

    @Test
    fun CM2_shareSurvivesEnsureDefaultAddonsRefreshAndInstall() =
        runTest {
            val cinemetaManifest = sampleManifest("org.stremio.cinemeta", "Cinemeta")
            val cinemetaRaw = sampleRawJson("org.stremio.cinemeta", "Cinemeta")
            coEvery { remoteDataSource.manifestWithRaw("https://v3-cinemeta.strem.io/") } returns
                Outcome.Success(FetchedManifest(cinemetaManifest, cinemetaRaw))
            coEvery { remoteDataSource.manifest("https://v3-cinemeta.strem.io/") } returns
                Outcome.Success(cinemetaManifest)

            val fakeAppState = CinemetaFakeAppStateRepo()
            val ensureDefault = EnsureDefaultAddons(repository, fakeAppState, activeProfile)

            val installOutcome = repository.install(CINEMETA_URL)
            val installed = (installOutcome as Outcome.Success).value
            repository.setShared(installed.transportUrl, true)

            ensureDefault()
            repository.refresh(CINEMETA_URL)
            repository.install(CINEMETA_URL)

            val addons = repository.observeAll().first()
            assertEquals(1, addons.size)
            assertTrue("Expected shared to remain true", addons[0].shared)
        }

    @Test
    fun CM3_setShared_unknownUrl_returnsNotFound() =
        runTest {
            val outcome = repository.setShared("https://unknown-addon.com/manifest.json", true)
            assertEquals(Outcome.Failure(AppError.NotFound), outcome)
        }

    @Test
    fun CM6_hiddenSharedAddon_isCounted() =
        runTest {
            val m = sampleManifest("org.shared.dupe", "Dupe Addon")
            val r = sampleRawJson("org.shared.dupe", "Dupe Addon")
            coEvery { remoteDataSource.manifestWithRaw("https://dupe.com/") } returns
                Outcome.Success(FetchedManifest(m, r))

            activeProfile.setActive(1L)
            repository.install("https://dupe.com")
            repository.setShared("https://dupe.com", true)

            activeProfile.setActive(2L)
            assertEquals(0, repository.observeHiddenSharedCount().first())

            repository.install("https://dupe.com")
            assertEquals(1, repository.observeHiddenSharedCount().first())
        }
}
