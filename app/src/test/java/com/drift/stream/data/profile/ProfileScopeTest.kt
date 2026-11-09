package com.drift.stream.data.profile

import com.drift.stream.testutil.FakeActiveProfileProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScopeTest {
    @Test
    fun PS1_scopedFlow_emitsNothingUntilAProfileIsResolved() =
        runTest(UnconfinedTestDispatcher()) {
            val provider = FakeActiveProfileProvider(initialId = null)
            val emissions = mutableListOf<String>()

            val job =
                launch {
                    provider.scopedFlow { id -> flowOf("profile-$id") }.toList(emissions)
                }

            assertEquals(emptyList<String>(), emissions)

            provider.setActive(7L)
            assertEquals(listOf("profile-7"), emissions)

            job.cancel()
        }

    @Test
    fun PS2_scopedFlow_switchesWhenTheProfileChanges() =
        runTest(UnconfinedTestDispatcher()) {
            val provider = FakeActiveProfileProvider(initialId = 1L)
            val emissions = mutableListOf<String>()

            val job =
                launch {
                    provider.scopedFlow { id -> flowOf("profile-$id") }.toList(emissions)
                }

            assertEquals(listOf("profile-1"), emissions)

            provider.setActive(2L)
            assertEquals(listOf("profile-1", "profile-2"), emissions)

            job.cancel()
        }

    @Test
    fun PS3_requireId_returnsTheActiveId_andThrowsAfterFiveSecondsWithoutOne() =
        runTest {
            val resolvedProvider = FakeActiveProfileProvider(initialId = 42L)
            assertEquals(42L, resolvedProvider.requireId())

            val emptyProvider = FakeActiveProfileProvider(initialId = null)
            var threw = false
            try {
                emptyProvider.requireId()
            } catch (e: IllegalStateException) {
                if (e.message == "No active profile") {
                    threw = true
                }
            }
            assertTrue(threw)
        }
}
