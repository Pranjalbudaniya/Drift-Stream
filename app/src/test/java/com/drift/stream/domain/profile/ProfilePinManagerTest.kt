package com.drift.stream.domain.profile

import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ProfilePinRepository
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.SetPinResult
import com.drift.stream.domain.repository.VerifyResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePinManagerTest {
    private val profileRepository = mockk<ProfileRepository>()
    private val profilePinRepository = mockk<ProfilePinRepository>()
    private val manager = ProfilePinManager(profileRepository, profilePinRepository)

    private fun sampleProfile(
        id: Long = 1L,
        isLocked: Boolean = false,
        isOwner: Boolean = true,
    ): Profile =
        Profile(
            id = id,
            name = "Me",
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = 0,
            isLocked = isLocked,
        )

    @Test
    fun PM1_setPin_valid_locksProfile() =
        runTest {
            coEvery { profileRepository.get(1L) } returns sampleProfile(1L, isLocked = false)
            coEvery { profilePinRepository.setPin(1L, "1234") } returns SetPinResult.Set

            val result = manager.setPin(1L, "1234")
            assertEquals(PinManagerSetResult.Set, result)
            coVerify(exactly = 1) { profilePinRepository.setPin(1L, "1234") }
        }

    @Test
    fun PM2_setPin_whenAlreadyLocked_isRefused() =
        runTest {
            coEvery { profileRepository.get(1L) } returns sampleProfile(1L, isLocked = true)

            val result = manager.setPin(1L, "1234")
            assertEquals(PinManagerSetResult.AlreadyLocked, result)
            coVerify(exactly = 0) { profilePinRepository.setPin(any(), any()) }
        }

    @Test
    fun PM3_changePin_correctCurrent_replacesPin() =
        runTest {
            coEvery { profilePinRepository.verify(1L, "1234") } returns VerifyResult.Correct
            coEvery { profilePinRepository.setPin(1L, "5678") } returns SetPinResult.Set

            val result = manager.changePin(1L, "1234", "5678")
            assertEquals(PinManagerChangeResult.Changed, result)
            coVerify(exactly = 1) { profilePinRepository.setPin(1L, "5678") }
        }

    @Test
    fun PM4_changePin_wrongCurrent_keepsOldPin_andCountsFailure() =
        runTest {
            coEvery { profilePinRepository.verify(1L, "0000") } returns VerifyResult.Wrong(4)

            val result = manager.changePin(1L, "0000", "5678")
            assertTrue(result is PinManagerChangeResult.Wrong)
            assertEquals(4, (result as PinManagerChangeResult.Wrong).triesLeft)
            coVerify(exactly = 0) { profilePinRepository.setPin(any(), any()) }
        }

    @Test
    fun PM5_removePin_correctCurrent_unlocks() =
        runTest {
            coEvery { profilePinRepository.verify(1L, "1234") } returns VerifyResult.Correct
            coEvery { profilePinRepository.removePin(1L) } returns Unit

            val result = manager.removePin(1L, "1234")
            assertEquals(PinManagerRemoveResult.Removed, result)
            coVerify(exactly = 1) { profilePinRepository.removePin(1L) }
        }

    @Test
    fun PM6_removePin_wrongCurrent_keepsPin() =
        runTest {
            coEvery { profilePinRepository.verify(1L, "0000") } returns VerifyResult.Wrong(4)

            val result = manager.removePin(1L, "0000")
            assertTrue(result is PinManagerRemoveResult.Wrong)
            assertEquals(4, (result as PinManagerRemoveResult.Wrong).triesLeft)
            coVerify(exactly = 0) { profilePinRepository.removePin(any()) }
        }

    @Test
    fun OR1_ownerReset_ownerLocked_correctOwnerPin_removesTargetPin() =
        runTest {
            val owner = sampleProfile(1L, isLocked = true, isOwner = true)
            val target = sampleProfile(2L, isLocked = true, isOwner = false)
            coEvery { profileRepository.get(1L) } returns owner
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profilePinRepository.verify(1L, "1234") } returns VerifyResult.Correct
            coEvery { profilePinRepository.removePin(2L) } returns Unit

            val result = manager.ownerResetPin(1L, "1234", 2L)
            assertEquals(OwnerResetPinResult.Removed, result)
            coVerify(exactly = 1) { profilePinRepository.removePin(2L) }
        }

    @Test
    fun OR2_ownerReset_ownerLocked_wrongOwnerPin_keepsTargetPin_countsFailure() =
        runTest {
            val owner = sampleProfile(1L, isLocked = true, isOwner = true)
            val target = sampleProfile(2L, isLocked = true, isOwner = false)
            coEvery { profileRepository.get(1L) } returns owner
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profilePinRepository.verify(1L, "0000") } returns VerifyResult.Wrong(4)

            val result = manager.ownerResetPin(1L, "0000", 2L)
            assertEquals(OwnerResetPinResult.WrongPin(4), result)
            coVerify(exactly = 0) { profilePinRepository.removePin(any()) }
        }

    @Test
    fun OR3_ownerReset_ownerNotLocked_removesTargetPin() =
        runTest {
            val owner = sampleProfile(1L, isLocked = false, isOwner = true)
            val target = sampleProfile(2L, isLocked = true, isOwner = false)
            coEvery { profileRepository.get(1L) } returns owner
            coEvery { profileRepository.get(2L) } returns target
            coEvery { profilePinRepository.removePin(2L) } returns Unit

            val result = manager.ownerResetPin(1L, null, 2L)
            assertEquals(OwnerResetPinResult.Removed, result)
            coVerify(exactly = 0) { profilePinRepository.verify(any(), any()) }
            coVerify(exactly = 1) { profilePinRepository.removePin(2L) }
        }

    @Test
    fun OR4_ownerReset_targetIsOwner_isRefused() =
        runTest {
            val result = manager.ownerResetPin(1L, "1234", 1L)
            assertEquals(OwnerResetPinResult.NotAllowed, result)
            coVerify(exactly = 0) { profilePinRepository.removePin(any()) }
        }

    @Test
    fun OR5_ownerReset_callerNotOwner_isRefused() =
        runTest {
            val nonOwner = sampleProfile(2L, isLocked = true, isOwner = false)
            val target = sampleProfile(3L, isLocked = true, isOwner = false)
            coEvery { profileRepository.get(2L) } returns nonOwner
            coEvery { profileRepository.get(3L) } returns target

            val result = manager.ownerResetPin(2L, "1234", 3L)
            assertEquals(OwnerResetPinResult.NotAllowed, result)
            coVerify(exactly = 0) { profilePinRepository.removePin(any()) }
        }
}
