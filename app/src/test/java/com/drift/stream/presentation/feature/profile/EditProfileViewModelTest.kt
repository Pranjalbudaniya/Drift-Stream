package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.data.profile.AvatarPhotoPipeline
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.CropState
import com.drift.stream.domain.profile.DeleteHost
import com.drift.stream.domain.profile.OwnerResetPinResult
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinManagerChangeResult
import com.drift.stream.domain.profile.PinManagerRemoveResult
import com.drift.stream.domain.profile.PinManagerSetResult
import com.drift.stream.domain.profile.PinPurpose
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.profile.ProfilePinManager
import com.drift.stream.domain.repository.DeleteResult
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val profileRepository = mockk<ProfileRepository>()
    private val activeProfileHolder = mockk<ActiveProfileHolder>()
    private val avatarPhotoStore = mockk<AvatarPhotoStore>(relaxed = true)
    private val avatarPhotoPipeline = mockk<AvatarPhotoPipeline>(relaxed = true)
    private val profilePinManager = mockk<ProfilePinManager>()
    private val pinChallenges = mockk<PinChallenges>()

    private val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    private val activeProfileIdFlow = MutableStateFlow<Long?>(null)

    private val fixedInstant = Instant.ofEpochMilli(1_700_000_000_000L)
    private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

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
        name: String,
        stamp: Long? = null,
        isLocked: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            avatarPhotoStamp = stamp,
            isOwner = id == 1L,
            needsSetup = false,
            isLocked = isLocked,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    private fun createViewModel(
        profileId: Long? = null,
        host: DeleteHost = DeleteHost.Picker,
        clock: Clock = fixedClock,
    ): EditProfileViewModel {
        val savedStateHandle =
            SavedStateHandle().apply {
                if (profileId != null) {
                    set("profileId", profileId)
                    set("host", host.name)
                }
            }
        return EditProfileViewModel(
            profileRepository = profileRepository,
            activeProfileHolder = activeProfileHolder,
            photoServices = AvatarPhotoServices(avatarPhotoStore, avatarPhotoPipeline),
            savedStateHandle = savedStateHandle,
            pinOps = ProfilePinOps(profilePinManager, pinChallenges),
            clock = clock,
        )
    }

    @Test
    fun EV1_save_updatesNameAndAvatar_withTrimmedName() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.update(any()) } answers {
                UpdateResult.Updated(firstArg())
            }

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("  Asha2 "))
            viewModel.onEvent(ProfileFormEvent.ColorPicked(3))
            viewModel.onEvent(ProfileFormEvent.EmojiPicked("🦊"))

            var receivedDone: EditProfileEffect.Done? = null
            val job =
                launch {
                    viewModel.effects.collect { effect ->
                        if (effect is EditProfileEffect.Done) {
                            receivedDone = effect
                        }
                    }
                }

            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                profileRepository.update(
                    match {
                        it.id == 2L &&
                            it.name == "Asha2" &&
                            it.avatarColor == 3 &&
                            it.avatarEmoji == "🦊"
                    },
                )
            }
            assertEquals(2L, receivedDone?.profileId)
            job.cancel()
        }

    @Test
    fun EV2_save_duplicateName_showsError_andKeepsInput() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val dev = sampleProfile(3L, "Dev")
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, target, dev)
            coEvery { profileRepository.get(2L) } returns target

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Dev"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { profileRepository.update(any()) }
            assertEquals(NameError.Duplicate, viewModel.uiState.value.formState.nameError)
            assertEquals("Dev", viewModel.uiState.value.formState.name)
        }

    @Test
    fun EV3_save_withNewPhoto_writesFileThenUpdatesWithStamp() =
        testScope.runTest {
            val pendingFile = tempFolder.newFile("pending.jpg")
            pendingFile.writeBytes(byteArrayOf(1, 2, 3))

            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.update(any()) } answers {
                UpdateResult.Updated(firstArg())
            }

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PhotoCropped(pendingFile.absolutePath))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                avatarPhotoStore.write(2L, any())
                profileRepository.update(
                    match {
                        it.id == 2L && it.avatarPhotoStamp == fixedInstant.toEpochMilli()
                    },
                )
            }
        }

    @Test
    fun EV4_save_removePhoto_updatesStampNullThenDeletesFile() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha", stamp = 999L)
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.update(any()) } answers {
                UpdateResult.Updated(firstArg())
            }

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.AvatarCleared)
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                profileRepository.update(
                    match {
                        it.id == 2L && it.avatarPhotoStamp == null
                    },
                )
                avatarPhotoStore.delete(2L)
            }
        }

    @Test
    fun EV5_save_photoUnchanged_doesNotTouchTheFile() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha", stamp = 999L)
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.update(any()) } answers {
                UpdateResult.Updated(firstArg())
            }

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha New"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { avatarPhotoStore.write(any(), any()) }
            coVerify(exactly = 0) { avatarPhotoStore.delete(any()) }
            coVerify(exactly = 1) {
                profileRepository.update(
                    match {
                        it.id == 2L && it.name == "Asha New" && it.avatarPhotoStamp == 999L
                    },
                )
            }
        }

    @Test
    fun EV6_save_updateFails_keepsInputAndShowsError() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.update(any()) } throws IOException("DB error")

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha Updated"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.formState.saving)
            assertTrue(viewModel.uiState.value.formState.saveFailed)
            assertEquals("Asha Updated", viewModel.uiState.value.formState.name)
        }

    @Test
    fun EV7_doubleSubmit_updatesOnce() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.update(any()) } answers {
                UpdateResult.Updated(firstArg())
            }

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.NameChanged("Asha Next"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepository.update(any()) }
        }

    @Test
    fun EV8_delete_confirmed_deletesAndCloses() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(target)
            activeProfileIdFlow.value = 1L
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.delete(2L) } returns DeleteResult.Deleted

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            var receivedDeleted: EditProfileEffect.Deleted? = null
            val job =
                launch {
                    viewModel.effects.collect { effect ->
                        if (effect is EditProfileEffect.Deleted) {
                            receivedDeleted = effect
                        }
                    }
                }

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepository.delete(2L) }
            assertEquals(2L, receivedDeleted?.profileId)
            job.cancel()
        }

    @Test
    fun EV9_delete_notAllowed_isRefusedByViewModel() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            profilesFlow.value = listOf(owner)
            coEvery { profileRepository.get(1L) } returns owner

            val viewModel = createViewModel(1L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { profileRepository.delete(any()) }
        }

    @Test
    fun EV10_delete_targetIsActiveInPicker_switchesToOwnerFirst() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, target)
            activeProfileIdFlow.value = 2L
            coEvery { profileRepository.get(2L) } returns target
            coEvery { activeProfileHolder.setActive(1L) } answers {
                activeProfileIdFlow.value = 1L
                true
            }
            coEvery { profileRepository.delete(2L) } returns DeleteResult.Deleted

            val viewModel = createViewModel(2L, host = DeleteHost.Picker)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                activeProfileHolder.setActive(1L)
                profileRepository.delete(2L)
            }
        }

    @Test
    fun EV11_delete_refusedActive_showsMessage() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, target)
            activeProfileIdFlow.value = 1L
            coEvery { profileRepository.get(1L) } returns owner
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.delete(2L) } returns DeleteResult.RefusedActive

            val viewModel = createViewModel(2L, host = DeleteHost.Settings)
            testScheduler.advanceUntilIdle()

            var closedOrDeleted = false
            val job =
                launch {
                    viewModel.effects.collect { effect ->
                        if (effect is EditProfileEffect.Deleted || effect is EditProfileEffect.Close) {
                            closedOrDeleted = true
                        }
                    }
                }

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            assertEquals(
                com.drift.stream.R.string.profile_delete_refused_active,
                viewModel.uiState.value.deleteErrorRes,
            )
            assertFalse(closedOrDeleted)
            job.cancel()
        }

    @Test
    fun EV12_leavingForm_deletesPendingPhotoFile() =
        testScope.runTest {
            val pendingFile = tempFolder.newFile("pending_to_delete.jpg")
            pendingFile.writeBytes(byteArrayOf(5, 6, 7))
            assertTrue(pendingFile.exists())

            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(ProfileFormEvent.PhotoCropped(pendingFile.absolutePath))
            testScheduler.advanceUntilIdle()

            viewModel.onCleared()
            assertFalse(pendingFile.exists())
        }

    @Test
    fun EV13_delete_doubleConfirm_deletesOnce() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(target)
            activeProfileIdFlow.value = 1L
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.delete(2L) } returns DeleteResult.Deleted

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profileRepository.delete(2L) }
        }

    @Test
    fun EV14_initialize_settingsHost_nonOwnerEditingOther_closes() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val nonOwner = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, nonOwner)
            activeProfileIdFlow.value = 2L
            coEvery { profileRepository.get(1L) } returns owner
            coEvery { profileRepository.get(2L) } returns nonOwner

            val viewModel = createViewModel(null)

            val effects = mutableListOf<EditProfileEffect>()
            val job = launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.initialize(1L, DeleteHost.Settings)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(EditProfileEffect.Close), effects)
            assertNull(viewModel.uiState.value.profile)
            job.cancel()
        }

    @Test
    fun EV15_initialize_settingsHost_nonOwnerEditingSelf_loads() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val nonOwner = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, nonOwner)
            activeProfileIdFlow.value = 2L
            coEvery { profileRepository.get(2L) } returns nonOwner

            val viewModel = createViewModel(2L, host = DeleteHost.Settings)
            testScheduler.advanceUntilIdle()

            assertEquals(
                2L,
                viewModel.uiState.value.profile
                    ?.id,
            )
            assertFalse(viewModel.uiState.value.canDelete)
        }

    @Test
    fun EV16_initialize_pickerHost_skipsRoleCheck() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val nonOwner = sampleProfile(2L, "Asha")
            profilesFlow.value = listOf(owner, nonOwner)
            activeProfileIdFlow.value = 2L
            coEvery { profileRepository.get(1L) } returns owner

            val viewModel = createViewModel(1L, host = DeleteHost.Picker)
            testScheduler.advanceUntilIdle()

            assertEquals(
                1L,
                viewModel.uiState.value.profile
                    ?.id,
            )
        }

    @Test
    fun EV17_setPin_whenUnlocked_asksCreateOnly_thenSets() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha", isLocked = false)
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Create) } returns PinResult.Created("1234")
            coEvery { profilePinManager.setPin(2L, "1234") } returns PinManagerSetResult.Set

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.uiState.value.pinLockEnabled)

            viewModel.onEvent(EditProfileEvent.SetPinClicked)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Create) }
            coVerify(exactly = 0) { pinChallenges.ask(match { it is PinRequest.Verify }) }
            coVerify(exactly = 1) { profilePinManager.setPin(2L, "1234") }
            assertFalse(viewModel.uiState.value.pinActionError)
        }

    @Test
    fun EV18_changePin_asksCurrentThenCreate_thenChanges() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha", isLocked = true)
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Verify(2L, PinPurpose.CurrentPinForChange)) } returns
                PinResult.Verified("1234")
            coEvery { pinChallenges.ask(PinRequest.Create) } returns PinResult.Created("5678")
            coEvery { profilePinManager.changePin(2L, "1234", "5678") } returns PinManagerChangeResult.Changed

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.pinLockEnabled)

            viewModel.onEvent(EditProfileEvent.ChangePinClicked)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                pinChallenges.ask(PinRequest.Verify(2L, PinPurpose.CurrentPinForChange))
                pinChallenges.ask(PinRequest.Create)
                profilePinManager.changePin(2L, "1234", "5678")
            }
            assertFalse(viewModel.uiState.value.pinActionError)
        }

    @Test
    fun EV19_removePin_asksCurrent_thenRemoves() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha", isLocked = true)
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Verify(2L, PinPurpose.CurrentPinForRemove)) } returns
                PinResult.Verified("1234")
            coEvery { profilePinManager.removePin(2L, "1234") } returns PinManagerRemoveResult.Removed

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.pinLockEnabled)

            viewModel.onEvent(EditProfileEvent.RemovePinClicked)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Verify(2L, PinPurpose.CurrentPinForRemove)) }
            coVerify(exactly = 0) { pinChallenges.ask(PinRequest.Create) }
            coVerify(exactly = 1) { profilePinManager.removePin(2L, "1234") }
            assertFalse(viewModel.uiState.value.pinActionError)
        }

    @Test
    fun EV20_changePin_cancelledAtAnyStep_keepsPin() =
        testScope.runTest {
            val target = sampleProfile(2L, "Asha", isLocked = true)
            profilesFlow.value = listOf(target)
            coEvery { profileRepository.get(2L) } returns target

            // Step A: Cancelled during current PIN verify
            coEvery { pinChallenges.ask(PinRequest.Verify(2L, PinPurpose.CurrentPinForChange)) } returns
                PinResult.Cancelled

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.ChangePinClicked)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { pinChallenges.ask(PinRequest.Create) }
            coVerify(exactly = 0) { profilePinManager.changePin(any(), any(), any()) }
            assertFalse(viewModel.uiState.value.pinActionError)

            // Step B: Verified current PIN, but Cancelled during new PIN create
            coEvery { pinChallenges.ask(PinRequest.Verify(2L, PinPurpose.CurrentPinForChange)) } returns
                PinResult.Verified("1234")
            coEvery { pinChallenges.ask(PinRequest.Create) } returns PinResult.Cancelled

            viewModel.onEvent(EditProfileEvent.ChangePinClicked)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { profilePinManager.changePin(any(), any(), any()) }
            assertFalse(viewModel.uiState.value.pinActionError)
        }

    @Test
    fun EV21_ownerReset_asksOwnerPin_thenRemovesTargetPin() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Owner", isLocked = true)
            val target = sampleProfile(2L, "Asha", isLocked = true)
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.OwnerReset)) } returns
                PinResult.Verified("9999")
            coEvery { profilePinManager.ownerResetPin(1L, "9999", 2L) } returns
                OwnerResetPinResult.Removed

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.pinLockEnabled)

            viewModel.onEvent(EditProfileEvent.ResetPinClicked)
            assertTrue(viewModel.uiState.value.showResetPinConfirm)

            viewModel.onEvent(EditProfileEvent.ResetPinConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.OwnerReset)) }
            coVerify(exactly = 1) { profilePinManager.ownerResetPin(1L, "9999", 2L) }
            assertFalse(viewModel.uiState.value.pinLockEnabled)
            assertFalse(viewModel.uiState.value.showResetPinConfirm)
        }

    @Test
    fun EV22_ownerReset_ownerNotLocked_noChallenge() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Owner", isLocked = false)
            val target = sampleProfile(2L, "Asha", isLocked = true)
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profilePinManager.ownerResetPin(1L, null, 2L) } returns
                OwnerResetPinResult.Removed

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.pinLockEnabled)

            viewModel.onEvent(EditProfileEvent.ResetPinConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { pinChallenges.ask(any()) }
            coVerify(exactly = 1) { profilePinManager.ownerResetPin(1L, null, 2L) }
            assertFalse(viewModel.uiState.value.pinLockEnabled)
        }

    @Test
    fun EV23_ownerReset_cancelledAtOwnerPin_keepsPin() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Owner", isLocked = true)
            val target = sampleProfile(2L, "Asha", isLocked = true)
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.OwnerReset)) } returns
                PinResult.Cancelled

            val viewModel = createViewModel(2L)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.pinLockEnabled)

            viewModel.onEvent(EditProfileEvent.ResetPinConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.OwnerReset)) }
            coVerify(exactly = 0) { profilePinManager.ownerResetPin(any(), any(), any()) }
            assertTrue(viewModel.uiState.value.pinLockEnabled)
        }

    @Test
    fun EV24_delete_ownerLocked_requiresOwnerPin_afterConfirm() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Owner", isLocked = true)
            val target = sampleProfile(2L, "Asha", isLocked = false)
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.DeleteProfile)) } returns
                PinResult.Verified("9999")
            coEvery { profileRepository.delete(2L) } returns DeleteResult.Deleted

            val viewModel = createViewModel(2L, host = DeleteHost.Picker)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerifyOrder {
                pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.DeleteProfile))
                profileRepository.delete(2L)
            }
        }

    @Test
    fun EV25_delete_ownerLocked_pinCancelled_doesNotDelete() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Owner", isLocked = true)
            val target = sampleProfile(2L, "Asha", isLocked = false)
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.DeleteProfile)) } returns
                PinResult.Cancelled

            val viewModel = createViewModel(2L, host = DeleteHost.Picker)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { pinChallenges.ask(PinRequest.Verify(1L, PinPurpose.DeleteProfile)) }
            coVerify(exactly = 0) { profileRepository.delete(any()) }
        }

    @Test
    fun EV26_delete_ownerNotLocked_noPinChallenge() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Owner", isLocked = false)
            val target = sampleProfile(2L, "Asha", isLocked = false)
            profilesFlow.value = listOf(owner, target)
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profileRepository.delete(2L) } returns DeleteResult.Deleted

            val viewModel = createViewModel(2L, host = DeleteHost.Picker)
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(EditProfileEvent.DeleteConfirmed)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { pinChallenges.ask(any()) }
            coVerify(exactly = 1) { profileRepository.delete(2L) }
        }
}
