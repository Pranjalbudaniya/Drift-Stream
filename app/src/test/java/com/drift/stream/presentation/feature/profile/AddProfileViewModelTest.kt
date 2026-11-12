package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.usecase.NameResult
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
            viewModel.onEvent(ProfileFormEvent.ColorPicked(3))
            viewModel.onEvent(ProfileFormEvent.EmojiPicked("🚀"))
            viewModel.onEvent(ProfileFormEvent.Submit)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.saving)
            assertTrue(state.saveFailed)
            assertEquals("Asha", state.name)
            assertEquals(3, state.colorIndex)
            assertEquals("🚀", state.emoji)
        }

    @Test
    fun AP4_doubleSubmit_createsOnce() =
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
}
