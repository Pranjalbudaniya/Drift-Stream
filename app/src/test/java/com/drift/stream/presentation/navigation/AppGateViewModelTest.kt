package com.drift.stream.presentation.navigation

import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinPurpose
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.profile.StartGate
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val pinChallenges = mockk<PinChallenges>()

    private val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    private val activeProfileIdFlow = MutableStateFlow<Long?>(null)
    private val alwaysShowPickerFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { profileRepository.profiles } returns profilesFlow
        every { activeProfileHolder.activeProfileId } returns activeProfileIdFlow
        every { appStateRepository.alwaysShowPicker } returns alwaysShowPickerFlow
        every { pinChallenges.release(any()) } returns Unit
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
        isLocked: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = needsSetup,
            isLocked = isLocked,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    private fun createViewModel(): AppGateViewModel =
        AppGateViewModel(
            profileRepository = profileRepository,
            activeProfileHolder = activeProfileHolder,
            appStateRepository = appStateRepository,
            pinChallenges = pinChallenges,
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

    @Test
    fun GV7_chooseLockedProfile_requiresPin_thenEnters() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val locked = sampleProfile(2L, "Asha", isOwner = false, isLocked = true)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner, locked)

            coEvery {
                pinChallenges.ask(
                    PinRequest.Verify(2L, PinPurpose.EnterProfile),
                    holdUntilReleased = true,
                )
            } returns PinResult.Verified("1234")
            coEvery { activeProfileHolder.setActive(2L) } coAnswers {
                activeProfileIdFlow.value = 2L
                true
            }

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onProfileChosen(2L)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                pinChallenges.ask(
                    PinRequest.Verify(2L, PinPurpose.EnterProfile),
                    holdUntilReleased = true,
                )
            }
            coVerify(exactly = 1) { activeProfileHolder.setActive(2L) }
            assertEquals(StartGate.Enter, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.isChoosing)
            assertFalse(vm.uiState.value.chooseFailed)
        }

    @Test
    fun GV8_chooseLockedProfile_pinCancelled_staysOnPicker() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val locked = sampleProfile(2L, "Asha", isOwner = false, isLocked = true)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner, locked)

            coEvery {
                pinChallenges.ask(
                    PinRequest.Verify(2L, PinPurpose.EnterProfile),
                    holdUntilReleased = true,
                )
            } returns PinResult.Cancelled

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onProfileChosen(2L)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                pinChallenges.ask(
                    PinRequest.Verify(2L, PinPurpose.EnterProfile),
                    holdUntilReleased = true,
                )
            }
            coVerify(exactly = 0) { activeProfileHolder.setActive(any()) }
            assertEquals(StartGate.Picker, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.isChoosing)
            assertFalse(vm.uiState.value.chooseFailed)
        }

    @Test
    fun GV9_chooseUnlockedProfile_noPinChallenge() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val unlocked = sampleProfile(2L, "Asha", isOwner = false, isLocked = false)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner, unlocked)

            coEvery { activeProfileHolder.setActive(2L) } coAnswers {
                activeProfileIdFlow.value = 2L
                true
            }

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onProfileChosen(2L)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { pinChallenges.ask(any()) }
            coVerify(exactly = 1) { activeProfileHolder.setActive(2L) }
            assertEquals(StartGate.Enter, vm.uiState.value.gate)
        }

    @Test
    fun GV10_manage_ownerLocked_requiresOwnerPin_thenManaging() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true, isLocked = true)
            profilesFlow.value = listOf(owner)

            coEvery { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.ManageProfiles)) } returns
                PinResult.Verified("1234")

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertFalse(vm.uiState.value.managing)

            vm.onManagePressed()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.ManageProfiles)) }
            assertTrue(vm.uiState.value.managing)
        }

    @Test
    fun GV11_manage_ownerLocked_pinCancelled_notManaging() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true, isLocked = true)
            profilesFlow.value = listOf(owner)

            coEvery { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.ManageProfiles)) } returns
                PinResult.Cancelled

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertFalse(vm.uiState.value.managing)

            vm.onManagePressed()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.ManageProfiles)) }
            assertFalse(vm.uiState.value.managing)
        }

    @Test
    fun GV12_manage_ownerNotLocked_opensDirectly() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true, isLocked = false)
            profilesFlow.value = listOf(owner)

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertFalse(vm.uiState.value.managing)

            vm.onManagePressed()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { pinChallenges.ask(any()) }
            assertTrue(vm.uiState.value.managing)
        }

    @Test
    fun GV13_manage_done_leavesManaging() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true, isLocked = false)
            profilesFlow.value = listOf(owner)

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()

            vm.onManagePressed()
            testScheduler.advanceUntilIdle()
            assertTrue(vm.uiState.value.managing)

            vm.onManageDone()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { pinChallenges.ask(any()) }
            assertFalse(vm.uiState.value.managing)
        }

    @Test
    fun GV14_chooseLockedProfile_releasesPadOnlyAfterGateLatches() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", isOwner = true)
            val p2 = sampleProfile(2L, "Asha", isOwner = false, isLocked = true)
            activeProfileIdFlow.value = 1L
            profilesFlow.value = listOf(owner, p2)

            coEvery {
                pinChallenges.ask(
                    PinRequest.Verify(2L, PinPurpose.EnterProfile),
                    holdUntilReleased = true,
                )
            } returns PinResult.Verified("1234")

            coEvery { activeProfileHolder.setActive(2L) } coAnswers {
                verify(exactly = 0) { pinChallenges.release(any()) }
                activeProfileIdFlow.value = 2L
                true
            }

            val vm = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(StartGate.Picker, vm.uiState.value.gate)

            vm.onProfileChosen(2L)
            testScheduler.advanceUntilIdle()

            assertEquals(StartGate.Enter, vm.uiState.value.gate)
            verify(exactly = 1) { pinChallenges.release(2L) }
        }
}
