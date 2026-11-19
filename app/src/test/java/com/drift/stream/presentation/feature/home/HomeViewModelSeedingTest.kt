package com.drift.stream.presentation.feature.home

import com.drift.stream.data.remote.addon.HomeRowLoader
import com.drift.stream.domain.model.AppError
import com.drift.stream.domain.model.HomeRowConfig
import com.drift.stream.domain.model.Outcome
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.AddonRepository
import com.drift.stream.domain.repository.ContentPrefsRepository
import com.drift.stream.domain.repository.HomeRowConfigRepository
import com.drift.stream.domain.repository.LibraryRepository
import com.drift.stream.domain.repository.ProgressRepository
import com.drift.stream.domain.usecase.EnsureDefaultAddons
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSeedingTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository: AddonRepository = mockk(relaxed = true)
    private val ensureDefaultAddons: EnsureDefaultAddons = mockk()
    private val homeRowLoader: HomeRowLoader = mockk(relaxed = true)
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val contentPrefsRepository: ContentPrefsRepository = mockk(relaxed = true)
    private val homeRowConfigRepository: HomeRowConfigRepository = mockk()
    private val activeProfileProvider: ActiveProfileProvider = mockk()

    private val homeRowConfigFlow = MutableStateFlow(HomeRowConfig())
    private val activeProfileIdFlow = MutableStateFlow<Long?>(1L)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { homeRowConfigRepository.config } returns homeRowConfigFlow
        every { activeProfileProvider.activeProfileId } returns activeProfileIdFlow
        coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            addonRepository = addonRepository,
            ensureDefaultAddons = ensureDefaultAddons,
            homeRowLoader = homeRowLoader,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            contentPrefsRepository = contentPrefsRepository,
            dispatcher = testDispatcher,
            homeRowConfigRepository = homeRowConfigRepository,
            activeProfileProvider = activeProfileProvider,
        )

    @Test
    fun SD1_firstActiveProfile_seedsDefaultAddonsOnce() =
        runTest(testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { ensureDefaultAddons() }
        }

    @Test
    fun SD2_activeProfileChange_seedsAgainForTheNewProfile() =
        runTest(testDispatcher) {
            val seededProfiles = mutableListOf<Long?>()
            coEvery { ensureDefaultAddons() } coAnswers {
                seededProfiles.add(activeProfileIdFlow.value)
                Outcome.Success(Unit)
            }

            createViewModel()
            advanceUntilIdle()

            assertEquals(listOf(1L), seededProfiles)

            activeProfileIdFlow.value = 2L
            advanceUntilIdle()

            assertEquals(listOf(1L, 2L), seededProfiles)
            coVerify(exactly = 2) { ensureDefaultAddons() }
        }

    @Test
    fun SD3_sameProfileEmittedAgain_doesNotSeedTwice() =
        runTest(testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { ensureDefaultAddons() }

            activeProfileIdFlow.value = 1L
            advanceUntilIdle()

            coVerify(exactly = 1) { ensureDefaultAddons() }
        }

    @Test
    fun SD4_seedFailure_isClearedWhenProfileChanges() =
        runTest(testDispatcher) {
            coEvery { ensureDefaultAddons() } returns Outcome.Failure(AppError.Network)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.seedFailed)

            coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
            activeProfileIdFlow.value = 2L
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.seedFailed)
        }

    @Test
    fun SD5_profileChangedDuringSeed_previousSeedIsCancelled() =
        runTest(testDispatcher) {
            coEvery { ensureDefaultAddons() } coAnswers {
                if (activeProfileIdFlow.value == 1L) {
                    delay(500)
                    Outcome.Failure(AppError.Network)
                } else {
                    Outcome.Success(Unit)
                }
            }

            val viewModel = createViewModel()
            advanceTimeBy(100)

            activeProfileIdFlow.value = 2L
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.seedFailed)
        }
}
