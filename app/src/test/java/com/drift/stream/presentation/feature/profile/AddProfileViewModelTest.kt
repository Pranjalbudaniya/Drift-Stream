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
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.usecase.NameResult
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
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddProfileViewModelTest {
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

    private fun sampleProfile(
        id: Long,
        name: String,
        color: Int = 0,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = color,
            avatarEmoji = null,
            isOwner = id == 1L,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    @Test
    fun AP1_submit_createsProfileWithChosenFields() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val createdProfile = sampleProfile(2L, "Asha", 5).copy(avatarEmoji = "🦊")
            coEvery {
                profileRepo.create(ProfileDraft("Asha", 5, "🦊"))
            } returns CreateResult.Created(createdProfile)

            val savedStateHandle = SavedStateHandle()
            val viewModel = AddProfileViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.ColorPicked(5))
            viewModel.onEvent(ProfileFormEvent.EmojiPicked("🦊"))

            var receivedDone: AddProfileEffect.Done? = null
            val collectJob =
                launch {
                    viewModel.effects.collect { effect ->
                        if (effect is AddProfileEffect.Done) {
                            receivedDone = effect
                        }
                    }
                }

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                profileRepo.create(ProfileDraft("Asha", 5, "🦊"))
            }
            assertEquals(AddProfileEffect.Done(2L), receivedDone)

            collectJob.cancel()
        }

    @Test
    fun AP2_duplicateName_blocksCreate() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val savedStateHandle = SavedStateHandle()
            val viewModel = AddProfileViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Me"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) {
                profileRepo.create(any())
            }
            assertEquals(NameError.Duplicate, viewModel.uiState.value.nameError)
        }

    @Test
    fun AP3_createFailure_keepsInput() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            coEvery {
                profileRepo.create(any())
            } returns CreateResult.InvalidName(NameResult.Blank)

            val savedStateHandle = SavedStateHandle()
            val viewModel = AddProfileViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.ColorPicked(4))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.saving)
            assertTrue(state.saveFailed)
            assertEquals("Asha", state.name)
            assertEquals(4, state.colorIndex)
        }

    @Test
    fun AP4_submit_ignoresRepeatedSubmit() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Asha", 1)
            coEvery {
                profileRepo.create(ProfileDraft("Asha", 1, null))
            } returns CreateResult.Created(created)

            val savedStateHandle = SavedStateHandle()
            val viewModel = AddProfileViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.ColorPicked(1))

            // Two quick submits
            viewModel.onEvent(ProfileFormEvent.Submit)
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                profileRepo.create(ProfileDraft("Asha", 1, null))
            }
        }

    @Test
    fun AP5_createWithPhoto_photoWriteFails_keepsProfileAndReportsPhotoError() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val photoStore = mockk<AvatarPhotoStore>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Bob", 3)
            coEvery {
                profileRepo.create(ProfileDraft("Bob", 3, null, null, null))
            } returns CreateResult.Created(created)

            coEvery {
                photoStore.write(2L, any())
            } throws IOException("Disk full")

            val testTime = 1_700_000_000_000L
            val testClock = Clock.fixed(Instant.ofEpochMilli(testTime), ZoneId.of("UTC"))

            val tempFile =
                File.createTempFile("pending_avatar", ".jpg").apply {
                    writeBytes(byteArrayOf(5, 6, 7, 8))
                    deleteOnExit()
                }

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    avatarPhotoStore = photoStore,
                    clock = testClock,
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: AddProfileEffect.Done? = null
            val collectJob =
                launch {
                    viewModel.effects.collect { effect ->
                        if (effect is AddProfileEffect.Done) {
                            receivedDone = effect
                        }
                    }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Bob"))
            viewModel.onEvent(ProfileFormEvent.ColorPicked(3))
            viewModel.onEvent(ProfileFormEvent.PhotoCropped(tempFile.absolutePath))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                profileRepo.create(ProfileDraft("Bob", 3, null, null, null))
            }
            coVerify(exactly = 1) {
                photoStore.write(2L, any())
            }
            coVerify(exactly = 0) {
                profileRepo.setAvatarPhoto(any(), any())
            }

            assertTrue(viewModel.uiState.value.photoError)
            assertEquals(AddProfileEffect.Done(2L, photoSaveFailed = true), receivedDone)

            collectJob.cancel()
        }

    @Test
    fun AP6_leavingForm_deletesPendingPhotoFile() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val tempFile =
                File.createTempFile("pending_avatar", ".jpg").apply {
                    writeBytes(byteArrayOf(9, 10, 11))
                }
            assertTrue(tempFile.exists())

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PhotoCropped(tempFile.absolutePath))
            assertEquals(tempFile.absolutePath, viewModel.uiState.value.pendingPhotoPath)

            viewModel.onCleared()
            assertFalse(tempFile.exists())
        }

    @Test
    fun AR1_pinRow_offByDefault() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val savedStateHandle = SavedStateHandle()
            val viewModel = AddProfileViewModel(profileRepo, savedStateHandle)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.pinPending.value)
            assertFalse(viewModel.uiState.value.pinPending)
        }

    @Test
    fun AR2_pinRow_setPin_created_marksPending_noCreateYet() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.pinPending.value)
            assertTrue(viewModel.uiState.value.pinPending)
            coVerify(exactly = 0) { profileRepo.create(any()) }
        }

    @Test
    fun AR3_pinRow_setPin_cancelled_leavesOff() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Cancelled

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
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
    fun AR4_pinRow_change_replacesPendingPin() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Asha", 1)
            coEvery { profileRepo.create(any()) } returns CreateResult.Created(created)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>()
            coEvery { challenges.ask(PinRequest.Create) } returnsMany
                listOf(
                    PinResult.Created("1234"),
                    PinResult.Created("4321"),
                )
            coEvery { manager.setPin(2L, "4321") } returns PinManagerSetResult.Set

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
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

            coVerify(exactly = 1) { manager.setPin(2L, "4321") }
            coVerify(exactly = 0) { manager.setPin(2L, "1234") }
        }

    @Test
    fun AR5_pinRow_remove_clearsPending_withoutPad() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Asha", 1)
            coEvery { profileRepo.create(any()) } returns CreateResult.Created(created)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
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

            coVerify(exactly = 1) { profileRepo.create(any()) }
            coVerify(exactly = 0) { manager.setPin(any(), any()) }
        }

    @Test
    fun AR6_create_withoutPin_createsWithoutSetPin() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Asha", 1)
            coEvery { profileRepo.create(any()) } returns CreateResult.Created(created)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>(relaxed = true)

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: AddProfileEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { if (it is AddProfileEffect.Done) receivedDone = it }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepo.create(any()) }
            coVerify(exactly = 0) { manager.setPin(any(), any()) }
            assertEquals(AddProfileEffect.Done(2L), receivedDone)
            job.cancel()
        }

    @Test
    fun AR7_create_withPendingPin_createsThenSetsPin() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Asha", 1)
            coEvery { profileRepo.create(any()) } returns CreateResult.Created(created)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>()
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")
            coEvery { manager.setPin(2L, "1234") } returns PinManagerSetResult.Set

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: AddProfileEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { if (it is AddProfileEffect.Done) receivedDone = it }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                profileRepo.create(any())
                manager.setPin(2L, "1234")
            }
            assertEquals(AddProfileEffect.Done(2L, pinSaveFailed = false), receivedDone)
            assertFalse(viewModel.pinSaveFailed)
            job.cancel()
        }

    @Test
    fun AR8_create_setPinFails_keepsProfile_reportsPinError() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me", 0)
            val profileRepo = mockk<ProfileRepository>()
            val profilesFlow = MutableStateFlow(listOf(owner))
            every { profileRepo.profiles } returns profilesFlow

            val created = sampleProfile(2L, "Asha", 1)
            coEvery { profileRepo.create(any()) } returns CreateResult.Created(created)

            val challenges = mockk<PinChallenges>()
            val manager = mockk<ProfilePinManager>()
            coEvery { challenges.ask(PinRequest.Create) } returns PinResult.Created("1234")
            coEvery { manager.setPin(2L, "1234") } returns PinManagerSetResult.AlreadyLocked

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                AddProfileViewModel(
                    profileRepository = profileRepo,
                    savedStateHandle = savedStateHandle,
                    pinOps = ProfilePinOps(manager, challenges),
                )
            testScheduler.advanceUntilIdle()

            var receivedDone: AddProfileEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { if (it is AddProfileEffect.Done) receivedDone = it }
                }

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha"))
            viewModel.onEvent(ProfileFormEvent.PinSetClicked)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepo.create(any()) }
            assertTrue(viewModel.pinSaveFailed)
            assertEquals(AddProfileEffect.Done(2L, pinSaveFailed = true), receivedDone)
            job.cancel()
        }
}
