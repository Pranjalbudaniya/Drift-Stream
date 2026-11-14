package com.drift.stream.presentation.feature.pin

import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.AppDataEraser
import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinPurpose
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import com.drift.stream.domain.repository.ProfilePinRepository
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.SetPinResult
import com.drift.stream.domain.repository.VerifyResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinPadViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val profilePinRepository = mockk<ProfilePinRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val challenges = mockk<PinChallenges>(relaxed = true)
    private val appDataEraser = mockk<AppDataEraser>(relaxed = true)

    private fun sampleProfile(id: Long = 1L): Profile =
        Profile(
            id = id,
            name = "Asha",
            avatarColor = 2,
            avatarEmoji = "🦊",
            isOwner = false,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
            isLocked = true,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { profileRepository.get(any()) } returns sampleProfile()
        coEvery { profilePinRepository.remainingLock(any()) } returns 0L
        coEvery { profilePinRepository.verify(any(), any()) } returns VerifyResult.Wrong(4)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PinPadViewModel =
        PinPadViewModel(
            profilePinRepository = profilePinRepository,
            profileRepository = profileRepository,
            challenges = challenges,
            appDataEraser = appDataEraser,
        )

    @Test
    fun PP1_digits_acceptedUpToFour_ignoredBeyond() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Create))
            testScheduler.advanceUntilIdle()

            repeat(7) {
                viewModel.onEvent(PinPadEvent.DigitPressed('1'))
            }

            assertEquals(4, viewModel.uiState.value.dotCount)
        }

    @Test
    fun PP2_backspace_removesLastDigit() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            repeat(3) {
                viewModel.onEvent(PinPadEvent.DigitPressed('1'))
            }
            assertEquals(3, viewModel.uiState.value.dotCount)

            viewModel.onEvent(PinPadEvent.Backspace)
            assertEquals(2, viewModel.uiState.value.dotCount)
        }

    @Test
    fun PP3_confirm_ignoredBelowFourDigits() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            repeat(3) {
                viewModel.onEvent(PinPadEvent.DigitPressed('1'))
            }
            viewModel.onEvent(PinPadEvent.Confirm)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { profilePinRepository.verify(any(), any()) }
        }

    @Test
    fun PP4_verify_correct_finishesVerifiedAndClears() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "1234") } returns VerifyResult.Correct

            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profilePinRepository.verify(1L, "1234") }
            verify(exactly = 1) { challenges.finish(1L, PinResult.Verified("1234")) }
            assertEquals(0, viewModel.uiState.value.dotCount)
        }

    @Test
    fun PP5_verify_wrong_showsTriesLeft_andClears() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "0000") } returns VerifyResult.Wrong(3)

            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            "0000".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.dotCount)
            assertEquals(PinMessage.Wrong(3), viewModel.uiState.value.message)
        }

    @Test
    fun PP6_verify_locked_disablesKeys_withCountdown() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "0000") } returns VerifyResult.Locked(30_000L)

            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            "0000".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.runCurrent()

            assertTrue(viewModel.uiState.value.locked)
            assertEquals(PinMessage.Locked(30), viewModel.uiState.value.message)

            // Further digits and Confirm ignored
            viewModel.onEvent(PinPadEvent.DigitPressed('9'))
            assertEquals(0, viewModel.uiState.value.dotCount)
        }

    @Test
    fun PP7_lockCountdown_ticksAndUnlocks() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "0000") } returns VerifyResult.Locked(30_000L)

            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            "0000".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.runCurrent()

            assertTrue(viewModel.uiState.value.locked)

            testScheduler.advanceTimeBy(30_000L)
            testScheduler.runCurrent()

            assertFalse(viewModel.uiState.value.locked)
            assertNull(viewModel.uiState.value.message)
        }

    @Test
    fun PP8_cancel_finishesCancelled() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(PinPadEvent.Cancel)
            verify(exactly = 1) { challenges.finish(1L, PinResult.Cancelled) }
        }

    @Test
    fun PP9_create_mismatch_restarts() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Create))
            testScheduler.advanceUntilIdle()

            assertEquals(PinTitleKind.CreatePin, viewModel.uiState.value.titleKind)

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            viewModel.onEvent(PinPadEvent.Confirm)

            assertEquals(PinTitleKind.ConfirmPin, viewModel.uiState.value.titleKind)
            assertEquals(0, viewModel.uiState.value.dotCount)

            "1235".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            viewModel.onEvent(PinPadEvent.Confirm)

            assertEquals(PinTitleKind.CreatePin, viewModel.uiState.value.titleKind)
            assertEquals(0, viewModel.uiState.value.dotCount)
            assertEquals(PinMessage.Mismatch, viewModel.uiState.value.message)
        }

    @Test
    fun PP10_create_match_finishesCreated() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Create))
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            viewModel.onEvent(PinPadEvent.Confirm)

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            viewModel.onEvent(PinPadEvent.Confirm)

            verify(exactly = 1) { challenges.finish(1L, PinResult.Created("1234")) }
        }

    @Test
    fun PP11_doubleConfirm_verifiesOnce() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "1234") } coAnswers {
                delay(500)
                VerifyResult.Correct
            }

            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceTimeBy(100)
            assertTrue(viewModel.uiState.value.busy)

            viewModel.onEvent(PinPadEvent.Confirm)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profilePinRepository.verify(1L, "1234") }
        }

    @Test
    fun PP12_verify_autoSubmitsAtFourthDigit() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "1234") } returns VerifyResult.Correct

            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { profilePinRepository.verify(1L, "1234") }
            verify(exactly = 1) { challenges.finish(1L, PinResult.Verified("1234")) }
        }

    @Test
    fun PP13_create_firstEntry_waitsForConfirmKey_thenAdvances() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Create))
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceUntilIdle()

            assertEquals(PinTitleKind.CreatePin, viewModel.uiState.value.titleKind)
            assertEquals(4, viewModel.uiState.value.dotCount)

            viewModel.onEvent(PinPadEvent.Confirm)
            testScheduler.advanceUntilIdle()

            assertEquals(PinTitleKind.ConfirmPin, viewModel.uiState.value.titleKind)
            assertEquals(0, viewModel.uiState.value.dotCount)
        }

    @Test
    fun PP14_create_secondEntry_waitsForConfirmKey_thenFinishes() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Create))
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            viewModel.onEvent(PinPadEvent.Confirm)

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceUntilIdle()

            verify(exactly = 0) { challenges.finish(any(), any()) }

            viewModel.onEvent(PinPadEvent.Confirm)
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { challenges.finish(1L, PinResult.Created("1234")) }
        }

    @Test
    fun PP15_verified_withHold_showsOpeningState_andIgnoresInput() =
        testScope.runTest {
            coEvery { profilePinRepository.verify(1L, "1234") } returns VerifyResult.Correct

            val viewModel = createViewModel()
            viewModel.initialize(
                PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile), holdUntilReleased = true),
            )
            testScheduler.advanceUntilIdle()

            "1234".forEach { viewModel.onEvent(PinPadEvent.DigitPressed(it)) }
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.holding)
            assertEquals(0, viewModel.uiState.value.dotCount)
            assertNull(viewModel.uiState.value.message)

            viewModel.onEvent(PinPadEvent.DigitPressed('9'))
            assertEquals(0, viewModel.uiState.value.dotCount)

            viewModel.onEvent(PinPadEvent.Confirm)
            coVerify(exactly = 1) { profilePinRepository.verify(1L, "1234") }
        }

    @Test
    fun FP1_forgotPin_ownerPad_showsEraseConfirmation() =
        testScope.runTest {
            coEvery { profileRepository.get(1L) } returns sampleProfile(1L).copy(isOwner = true)
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(PinPadEvent.ForgotPinPressed)

            assertEquals(ForgotState.OwnerErase, viewModel.uiState.value.forgot)
        }

    @Test
    fun FP2_eraseConfirmed_callsEraser() =
        testScope.runTest {
            coEvery { profileRepository.get(1L) } returns sampleProfile(1L).copy(isOwner = true)
            coEvery { appDataEraser.eraseEverything() } returns true
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(PinPadEvent.ForgotPinPressed)
            viewModel.onEvent(PinPadEvent.EraseConfirmed)

            verify(exactly = 1) { appDataEraser.eraseEverything() }
        }

    @Test
    fun FP3_eraseDismissed_doesNothing() =
        testScope.runTest {
            coEvery { profileRepository.get(1L) } returns sampleProfile(1L).copy(isOwner = true)
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(PinPadEvent.ForgotPinPressed)
            viewModel.onEvent(PinPadEvent.EraseDismissed)

            assertNull(viewModel.uiState.value.forgot)
            verify(exactly = 0) { appDataEraser.eraseEverything() }
        }

    @Test
    fun FP4_forgotPin_nonOwnerPad_showsInfoOnly() =
        testScope.runTest {
            coEvery { profileRepository.get(2L) } returns sampleProfile(2L).copy(isOwner = false)
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(2L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(PinPadEvent.ForgotPinPressed)
            assertEquals(ForgotState.OthersInfo, viewModel.uiState.value.forgot)

            viewModel.onEvent(PinPadEvent.ForgotInfoDismissed)
            assertNull(viewModel.uiState.value.forgot)
            verify(exactly = 0) { appDataEraser.eraseEverything() }
        }

    @Test
    fun FP5_eraseFails_showsMessage() =
        testScope.runTest {
            coEvery { profileRepository.get(1L) } returns sampleProfile(1L).copy(isOwner = true)
            coEvery { appDataEraser.eraseEverything() } returns false
            val viewModel = createViewModel()
            viewModel.initialize(PendingPin(1L, PinRequest.Verify(1L, PinPurpose.EnterProfile)))
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(PinPadEvent.ForgotPinPressed)
            viewModel.onEvent(PinPadEvent.EraseConfirmed)

            assertEquals(ForgotState.EraseFailed, viewModel.uiState.value.forgot)
        }
}
