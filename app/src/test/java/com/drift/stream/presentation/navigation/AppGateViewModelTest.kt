package com.drift.stream.presentation.navigation

import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.StartGate
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class AppGateViewModelTest {
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
        name: String = "Profile $id",
        isOwner: Boolean = false,
        needsSetup: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = needsSetup,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    private fun createViewModel(): AppGateViewModel =
        AppGateViewModel(
            profileRepository = profileRepository,
            activeProfileHolder = activeProfileHolder,
            appStateRepository = appStateRepository,
        )

    @Test
    fun GV1_gate_followsDecideStartGate_forRepositoryFlows() =
        testScope.runTest {
            val vm = createViewModel()
            testScheduler.advanceUntilIdle()

            // Initial: empty profiles -> Resolving
            assertEquals(StartGate.Resolving, vm.uiState.value.gate)

            // Emit owner needing setup, active 1
            val owner = sampleProfile(1L, "Me", isOwner = true, needsSetup = true)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner)
            testScheduler.advanceUntilIdle()

            assertEquals(StartGate.Setup(1L), vm.uiState.value.gate)
        }

    @Test
    fun GV2_choose_marksChosen_afterHolderReportsId() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner, p2)

            coEvery { activeProfileHolder.setActive(2L) } coAnswers {
                activeProfileIdFlow.value = 2L
                true
            }

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onProfileChosen(2L)
            testScheduler.advanceUntilIdle()

            assertEquals(StartGate.Enter, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.isChoosing)
            assertFalse(vm.uiState.value.chooseFailed)
        }

    @Test
    fun GV3_choose_failureOrTimeout_staysOnPickerWithError() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner, p2)

            coEvery { activeProfileHolder.setActive(2L) } returns false

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onProfileChosen(2L)
            testScheduler.advanceUntilIdle()

            assertEquals(StartGate.Picker, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.isChoosing)
            assertTrue(vm.uiState.value.chooseFailed)
        }

    @Test
    fun GV4_setupCompleted_activatesOwnerAndMarksChosen() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            activeProfileIdFlow.value = 2L
            profilesFlow.value = listOf(owner, p2)

            coEvery { activeProfileHolder.setActive(1L) } coAnswers {
                activeProfileIdFlow.value = 1L
                true
            }

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onSetupCompleted(1L)
            testScheduler.advanceUntilIdle()

            coVerify { activeProfileHolder.setActive(1L) }
            assertEquals(StartGate.Enter, vm.uiState.value.gate)
        }

    @Test
    fun GV5_enterIsLatched_whenProfileListChangesLater() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner)

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Enter, vm.uiState.value.gate)

            // Later, a second profile appears in repository
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            profilesFlow.value = listOf(owner, p2)
            testScheduler.advanceUntilIdle()

            // Latched to Enter!
            assertEquals(StartGate.Enter, vm.uiState.value.gate)
        }

    @Test
    fun GV6_profilesPassThroughInRepositoryOrder() =
        testScope.runTest {
            val p1 = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(p2, p1)

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(p2, p1), vm.uiState.value.profiles)
        }
}
