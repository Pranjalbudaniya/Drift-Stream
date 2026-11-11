package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
                profileRepo.completeSetup(1L, "Asha", 2, "🦊")
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
                profileRepo.completeSetup(1L, "Asha", 2, "🦊")
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
                profileRepo.completeSetup(any(), any(), any(), any())
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
}
