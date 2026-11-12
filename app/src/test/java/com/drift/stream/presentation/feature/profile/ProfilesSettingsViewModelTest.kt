package com.drift.stream.presentation.feature.profile

import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ProfilesSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val profileRepository = mockk<ProfileRepository>()
    private val activeProfileHolder = mockk<ActiveProfileHolder>()
    private val appStateRepository = mockk<AppStateRepository>()

    private val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    private val activeProfileIdFlow = MutableStateFlow<Long?>(null)
    private val alwaysShowPickerFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { profileRepository.profiles } returns profilesFlow
        every { activeProfileHolder.activeProfileId } returns activeProfileIdFlow
        every { appStateRepository.alwaysShowPicker } returns alwaysShowPickerFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(
        id: Long,
        name: String,
        isOwner: Boolean = id == 1L,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    @Test
    fun PL1_list_marksOwnerAndCurrent() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val nonOwner = sampleProfile(2L, "Asha", isOwner = false)
            profilesFlow.value = listOf(owner, nonOwner)
            activeProfileIdFlow.value = 2L

            val viewModel = ProfilesSettingsViewModel(profileRepository, activeProfileHolder, appStateRepository)
            val job = launch { viewModel.uiState.collect {} }
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.profiles.size)

            val firstItem = state.profiles[0]
            assertEquals(1L, firstItem.profile.id)
            assertTrue(firstItem.isOwner)
            assertFalse(firstItem.isCurrent)

            val secondItem = state.profiles[1]
            assertEquals(2L, secondItem.profile.id)
            assertFalse(secondItem.isOwner)
            assertTrue(secondItem.isCurrent)

            assertEquals(2L, state.activeProfile?.id)
            job.cancel()
        }

    @Test
    fun PL2_list_followsRepositoryOrder() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            val p3 = sampleProfile(3L, "Dev", isOwner = false)

            profilesFlow.value = listOf(p2, p1, p3)
            activeProfileIdFlow.value = 1L

            val viewModel = ProfilesSettingsViewModel(profileRepository, activeProfileHolder, appStateRepository)
            val job = launch { viewModel.uiState.collect {} }
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            val ids = state.profiles.map { it.profile.id }
            assertEquals(listOf(2L, 1L, 3L), ids)
            job.cancel()
        }

    @Test
    fun PL3_uiState_ownerActive_canManage() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            profilesFlow.value = listOf(owner)
            activeProfileIdFlow.value = 1L

            val viewModel = ProfilesSettingsViewModel(profileRepository, activeProfileHolder, appStateRepository)
            val job = launch { viewModel.uiState.collect {} }
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.canManage)
            job.cancel()
        }

    @Test
    fun PL4_uiState_nonOwnerActive_cannotManage() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val nonOwner = sampleProfile(2L, "Asha", isOwner = false)
            profilesFlow.value = listOf(owner, nonOwner)
            activeProfileIdFlow.value = 2L

            val viewModel = ProfilesSettingsViewModel(profileRepository, activeProfileHolder, appStateRepository)
            val job = launch { viewModel.uiState.collect {} }
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canManage)
            job.cancel()
        }

    @Test
    fun PL5_alwaysShowPicker_reflectsAppState_andToggles() =
        testScope.runTest {
            alwaysShowPickerFlow.value = false
            coEvery { appStateRepository.setAlwaysShowPicker(any()) } coAnswers {
                alwaysShowPickerFlow.value = firstArg()
            }

            val viewModel = ProfilesSettingsViewModel(profileRepository, activeProfileHolder, appStateRepository)
            val job = launch { viewModel.uiState.collect {} }
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.alwaysShowPicker)

            viewModel.onEvent(ProfilesSettingsEvent.SetAlwaysShowPicker(true))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { appStateRepository.setAlwaysShowPicker(true) }
            assertTrue(viewModel.uiState.value.alwaysShowPicker)
            job.cancel()
        }
}
