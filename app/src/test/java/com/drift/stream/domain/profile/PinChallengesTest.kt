package com.drift.stream.domain.profile

import com.drift.stream.data.profile.DefaultPinChallenges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinChallengesTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun CC1_ask_returnsResultGivenToFinish() =
        testScope.runTest {
            val challenges = DefaultPinChallenges()
            val deferredResult =
                async {
                    challenges.ask(PinRequest.Create)
                }
            testScheduler.advanceUntilIdle()

            val pending = challenges.pending.value
            requireNotNull(pending)

            challenges.finish(pending.id, PinResult.Created("1234"))
            testScheduler.advanceUntilIdle()

            assertEquals(PinResult.Created("1234"), deferredResult.await())
            assertNull(challenges.pending.value)
        }

    @Test
    fun CC2_ask_cancelled_returnsCancelled() =
        testScope.runTest {
            val challenges = DefaultPinChallenges()
            val deferredResult =
                async {
                    challenges.ask(PinRequest.Verify(1L, PinPurpose.EnterProfile))
                }
            testScheduler.advanceUntilIdle()

            val pending = challenges.pending.value
            requireNotNull(pending)

            challenges.finish(pending.id, PinResult.Cancelled)
            testScheduler.advanceUntilIdle()

            assertEquals(PinResult.Cancelled, deferredResult.await())
            assertNull(challenges.pending.value)
        }

    @Test
    fun CC3_secondAsk_waitsForTheFirst() =
        testScope.runTest {
            val challenges = DefaultPinChallenges()

            val firstAsk = async { challenges.ask(PinRequest.Create) }
            testScheduler.advanceUntilIdle()

            val firstPending = challenges.pending.value
            requireNotNull(firstPending)
            assertEquals(PinRequest.Create, firstPending.request)

            val secondAsk = async { challenges.ask(PinRequest.Verify(2L, PinPurpose.EnterProfile)) }
            testScheduler.advanceUntilIdle()

            // The pending request is still the first one
            assertEquals(firstPending.id, challenges.pending.value?.id)

            // Complete the first
            challenges.finish(firstPending.id, PinResult.Created("1234"))
            testScheduler.advanceUntilIdle()

            assertEquals(PinResult.Created("1234"), firstAsk.await())

            // Now second request becomes pending
            val secondPending = challenges.pending.value
            requireNotNull(secondPending)
            assertEquals(PinRequest.Verify(2L, PinPurpose.EnterProfile), secondPending.request)

            challenges.finish(secondPending.id, PinResult.Cancelled)
            testScheduler.advanceUntilIdle()
            assertEquals(PinResult.Cancelled, secondAsk.await())
        }

    @Test
    fun CC4_callerCancelled_clearsPending() =
        testScope.runTest {
            val challenges = DefaultPinChallenges()
            val job =
                launch {
                    challenges.ask(PinRequest.Create)
                }
            testScheduler.advanceUntilIdle()

            val pending = challenges.pending.first { it != null }
            requireNotNull(pending)

            job.cancel()
            testScheduler.advanceUntilIdle()

            assertNull(challenges.pending.value)
        }
}
