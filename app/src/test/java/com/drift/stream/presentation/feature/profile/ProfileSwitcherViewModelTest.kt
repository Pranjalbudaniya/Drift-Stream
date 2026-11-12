package com.drift.stream.presentation.feature.profile

import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ProfileSwitcherViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val profileRepository = mockk<ProfileRepository>()
    private val activeProfileHolder = mockk<ActiveProfileHolder>()

    private val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    private val activeProfileIdFlow = MutableStateFlow<Long?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { profileRepository.profiles } returns profilesFlow
        every { activeProfileHolder.activeProfileId } returns activeProfileIdFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(
        id: Long,
        name: String = "Profile $id",
        isOwner: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = name,
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

    private fun createViewModel(): ProfileSwitcherViewModel =
        ProfileSwitcherViewModel(
            profileRepository = profileRepository,
            activeProfileHolder = activeProfileHolder,
        )

    @Test
    fun SW1_uiState_listsProfilesInOrder_andMarksCurrent() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            val p3 = sampleProfile(3L, "Dev", isOwner = false)

            profilesFlow.value = listOf(p1, p2, p3)
            activeProfileIdFlow.value = 2L

            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(p1, p2, p3), state.profiles)
            assertEquals(p2, state.activeProfile)
        }

    @Test
    fun SW2_switch_toOtherProfile_setsActive_waitsForHolder_thenEmitsSwitched() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            val p3 = sampleProfile(3L, "Dev", isOwner = false)

            profilesFlow.value = listOf(p1, p2, p3)
            activeProfileIdFlow.value = 2L

            coEvery { activeProfileHolder.setActive(3L) } coAnswers {
                activeProfileIdFlow.value = 3L
                true
            }

            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val effects = mutableListOf<ProfileSwitcherEffect>()
            val job = launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onEvent(ProfileSwitcherEvent.ProfileTapped(3L))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { activeProfileHolder.setActive(3L) }
            assertEquals(listOf(ProfileSwitcherEffect.Switched), effects)

            job.cancel()
        }

    @Test
    fun SW3_switch_toActiveProfile_closesWithoutSetActive() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)

            profilesFlow.value = listOf(p1, p2)
            activeProfileIdFlow.value = 2L

            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val effects = mutableListOf<ProfileSwitcherEffect>()
            val job = launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onEvent(ProfileSwitcherEvent.ProfileTapped(2L))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { activeProfileHolder.setActive(any()) }
            assertEquals(listOf(ProfileSwitcherEffect.Close), effects)

            job.cancel()
        }

    @Test
    fun SW4_switch_failureOrTimeout_staysWithError() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)

            profilesFlow.value = listOf(p1, p2)
            activeProfileIdFlow.value = 1L

            coEvery { activeProfileHolder.setActive(2L) } returns false

            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val effects = mutableListOf<ProfileSwitcherEffect>()
            val job = launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onEvent(ProfileSwitcherEvent.ProfileTapped(2L))
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.switching)
            assertTrue(effects.isEmpty())

            job.cancel()
        }

    @Test
    fun SW5_switch_whileSwitching_isIgnored() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            val p3 = sampleProfile(3L, "Dev", isOwner = false)

            profilesFlow.value = listOf(p1, p2, p3)
            activeProfileIdFlow.value = 2L

            coEvery { activeProfileHolder.setActive(3L) } coAnswers {
                delay(500)
                activeProfileIdFlow.value = 3L
                true
            }

            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileSwitcherEvent.ProfileTapped(3L))
            testScheduler.advanceTimeBy(100)
            assertTrue(viewModel.uiState.value.switching)

            viewModel.onEvent(ProfileSwitcherEvent.ProfileTapped(1L))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { activeProfileHolder.setActive(any()) }
            coVerify(exactly = 1) { activeProfileHolder.setActive(3L) }
            coVerify(exactly = 0) { activeProfileHolder.setActive(1L) }
        }

    @Test
    fun SW6_addProfile_offeredOnlyForOwner() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val nonOwner = sampleProfile(2L, "Asha", isOwner = false)

            profilesFlow.value = listOf(owner, nonOwner)
            activeProfileIdFlow.value = 1L

            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canAdd)

            activeProfileIdFlow.value = 2L
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canAdd)
        }
}
