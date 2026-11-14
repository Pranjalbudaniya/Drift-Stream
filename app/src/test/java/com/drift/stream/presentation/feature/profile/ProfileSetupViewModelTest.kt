package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinManagerSetResult
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.profile.ProfilePinManager
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleOwner(
        id: Long = 1L,
        name: String = "Me",
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 2,
            avatarEmoji = "🦊",
            isOwner = true,
            needsSetup = true,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
        )

    @Test
    fun SV1_submit_writesOnce_withTrimmedName() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            coEvery {
                profileRepo.completeSetup(1L, ProfileDraft("Asha", 2, "🦊"))
            } returns UpdateResult.Updated(owner.copy(name = "Asha", needsSetup = false))

            val savedStateHandle = SavedStateHandle()
            val viewModel = ProfileSetupViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            // Change name with spaces
            viewModel.onEvent(ProfileFormEvent.NameChanged("  Asha  "))

            var receivedDone: SetupEffect.Done? = null
            val collectJob =
                launch {
                    viewModel.effects.collect { effect ->
                        if (effect is SetupEffect.Done) {
                            receivedDone = effect
                        }
                    }
                }

            // Two quick submits
            viewModel.onEvent(ProfileFormEvent.Submit)
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                profileRepo.completeSetup(1L, ProfileDraft("Asha", 2, "🦊"))
            }
            assertEquals(SetupEffect.Done(1L), receivedDone)

            collectJob.cancel()
        }

    @Test
    fun SV2_submit_failure_showsError_andKeepsInput() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            coEvery {
                profileRepo.completeSetup(any(), any())
            } returns UpdateResult.NotFound

            val savedStateHandle = SavedStateHandle()
            val viewModel = ProfileSetupViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.ColorPicked(7))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.saving)
            assertTrue(state.saveFailed)
            assertEquals("Asha", state.name)
            assertEquals(7, state.colorIndex)
        }

    @Test
    fun SV3_submitWithPhoto_writesFileThenSetsStamp() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val photoStore = mockk<AvatarPhotoStore>(relaxed = true)
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val testTime = 1_700_000_000_000L
            val testClock = Clock.fixed(Instant.ofEpochMilli(testTime), ZoneId.of("UTC"))

            val tempFile =
                File.createTempFile("pending_avatar", ".jpg").apply {
                    writeBytes(byteArrayOf(1, 2, 3, 4))
                    deleteOnExit()
                }

            coEvery {
                profileRepo.completeSetup(
                    id = 1L,
                    draft =
                        ProfileDraft(
                            name = "Me",
                            avatarColor = 2,
                            avatarEmoji = null,
                            avatarCustomColor = null,
                            avatarPhotoStamp = testTime,
                        ),
                )
            } returns UpdateResult.Updated(owner.copy(avatarPhotoStamp = testTime))

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    avatarPhotoStore = photoStore,
                    clock = testClock,
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PhotoCropped(tempFile.absolutePath))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                photoStore.write(1L, match { it.contentEquals(byteArrayOf(1, 2, 3, 4)) })
            }
            coVerify(exactly = 1) {
                profileRepo.completeSetup(
                    id = 1L,
                    draft =
                        ProfileDraft(
                            name = "Me",
                            avatarColor = 2,
                            avatarEmoji = null,
                            avatarCustomColor = null,
                            avatarPhotoStamp = testTime,
                        ),
                )
            }
        }

    @Test
    fun SR1_pinRow_offByDefault() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val savedStateHandle = SavedStateHandle()
            val viewModel = ProfileSetupViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.pinPending.value)
            assertFalse(viewModel.uiState.value.pinPending)
        }

    @Test
    fun SR2_pinRow_setPin_created_marksPending_noSaveYet() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.pinPending.value)
            assertTrue(viewModel.uiState.value.pinPending)
            coVerify(exactly = 0) { profileRepo.completeSetup(any(), any()) }
        }

    @Test
    fun SR3_pinRow_setPin_cancelled_leavesOff() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Cancelled

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.pinPending.value)
            assertFalse(viewModel.uiState.value.pinPending)
        }

    @Test
    fun SR4_pinRow_change_replacesPendingPin() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow
            coEvery { profileRepo.completeSetup(any(), any()) } returns UpdateResult.Updated(owner)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>()
            coEvery { challenges.ask(PinRequest.Create) } returnsMany
                listOf(
                    PinResult.Created("1234"),
                    PinResult.Created("4321"),
                )
            coEvery { manager.setPin(1L, "4321") } returns PinManagerSetResult.Set

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.pinPending.value)

            viewModel.onEvent(ProfileFormEvent.PinChangeClicked)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.pinPending.value)

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { manager.setPin(1L, "4321") }
            coVerify(exactly = 0) { manager.setPin(1L, "1234") }
        }

    @Test
    fun SR5_pinRow_remove_clearsPending_withoutPad() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow
            coEvery { profileRepo.completeSetup(any(), any()) } returns UpdateResult.Updated(owner)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.pinPending.value)

            viewModel.onEvent(ProfileFormEvent.PinRemoveClicked)
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.pinPending.value)
            assertFalse(viewModel.uiState.value.pinPending)

            // No second ask call on remove
            coVerify(exactly = 1) { challenges.ask(any()) }

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepo.completeSetup(1L, any()) }
            coVerify(exactly = 0) { manager.setPin(any(), any()) }
        }

    @Test
    fun SR6_save_withoutPin_savesWithoutSetPin() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow
            coEvery { profileRepo.completeSetup(any(), any()) } returns UpdateResult.Updated(owner)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: SetupEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { if (it is SetupEffect.Done) receivedDone = it }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepo.completeSetup(1L, any()) }
            coVerify(exactly = 0) { manager.setPin(any(), any()) }
            assertEquals(SetupEffect.Done(1L), receivedDone)
            job.cancel()
        }

    @Test
    fun SR7_save_withPendingPin_savesThenSetsPin() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow
            coEvery { profileRepo.completeSetup(any(), any()) } returns UpdateResult.Updated(owner)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>()
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")
            coEvery { manager.setPin(1L, "1234") } returns PinManagerSetResult.Set

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: SetupEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { if (it is SetupEffect.Done) receivedDone = it }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                profileRepo.completeSetup(1L, any())
                manager.setPin(1L, "1234")
            }
            assertEquals(SetupEffect.Done(1L, pinSaveFailed = false), receivedDone)
            assertFalse(viewModel.pinSaveFailed)
            job.cancel()
        }

    @Test
    fun SR8_save_setPinFails_profileSaved_reportsPinError() =
        testScope.runTest {
            val owner = sampleOwner(1L, "Me")
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow
            coEvery { profileRepo.completeSetup(any(), any()) } returns UpdateResult.Updated(owner)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>()
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")
            coEvery { manager.setPin(1L, "1234") } returns PinManagerSetResult.AlreadyLocked

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                ProfileSetupViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: SetupEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { if (it is SetupEffect.Done) receivedDone = it }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepo.completeSetup(1L, any()) }
            assertTrue(viewModel.pinSaveFailed)
            assertEquals(SetupEffect.Done(1L, pinSaveFailed = true), receivedDone)
            job.cancel()
        }
}
