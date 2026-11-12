package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.data.profile.AvatarPhotoPipeline
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.DeleteHost
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
        color: Int = 0,
        emoji: String? = null,
        stamp: Long? = null,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = color,
            avatarEmoji = emoji,
            avatarPhotoStamp = stamp,
            isOwner = id == 1L,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    private fun createViewModel(
        profileId: Long,
        host: DeleteHost = DeleteHost.Picker,
        clock: Clock = fixedClock,
    ): EditProfileViewModel {
        val savedStateHandle =
            SavedStateHandle().apply {
                set("profileId", profileId)
                set("host", host.name)
            }
        return EditProfileViewModel(
            profileRepository = profileRepository,
            activeProfileHolder = activeProfileHolder,
            avatarPhotoStore = avatarPhotoStore,
            avatarPhotoPipeline = avatarPhotoPipeline,
            savedStateHandle = savedStateHandle,
            clock = clock,
        )
    }

    @Test
    fun EV1_save_updatesNameAndAvatar_withTrimmedName() =
        testScope.runTest {
            val owner = sampleProfile(1L, "Me")
            val target = sampleProfile(2L, "Asha", color = 0)
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

            val savedStateHandle = SavedStateHandle()
            val viewModel =
                EditProfileViewModel(
                    profileRepository = profileRepository,
                    activeProfileHolder = activeProfileHolder,
                    avatarPhotoStore = avatarPhotoStore,
                    avatarPhotoPipeline = avatarPhotoPipeline,
                    savedStateHandle = savedStateHandle,
                    clock = fixedClock,
                )

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
}
