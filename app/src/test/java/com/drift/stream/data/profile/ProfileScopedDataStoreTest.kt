package com.drift.stream.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drift.stream.testutil.FakeActiveProfileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScopedDataStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createStores(): Pair<ProfileDataStores, DataStore<Preferences>> {
        val legacyFile = File(tmpFolder.root, "settings.preferences_pb")
        val legacySettings: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher),
                produceFile = { legacyFile },
            )
        val stores =
            ProfileDataStores(
                fileFor = { name -> File(tmpFolder.root, "$name.preferences_pb") },
                newScope = { CoroutineScope(testDispatcher) },
                legacySettings = legacySettings,
            )
        return Pair(stores, legacySettings)
    }

    @Test
    fun SDS1_scopedStore_followsTheActiveProfile() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(1L)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)
            val key = stringPreferencesKey("pref_key")

            stores.storeFor(PersonalStore.SETTINGS, 1L).edit { prefs -> prefs[key] = "val1" }
            stores.storeFor(PersonalStore.SETTINGS, 2L).edit { prefs -> prefs[key] = "val2" }

            assertEquals("val1", scoped.data.first()[key])

            active.setActive(2L)
            assertEquals("val2", scoped.data.first()[key])
        }

    @Test
    fun SDS2_scopedStore_writeGoesToTheActiveProfilesStore() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(2L)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)
            val key = stringPreferencesKey("write_key")

            scoped.edit { prefs -> prefs[key] = "p2_val" }

            assertNull(stores.storeFor(PersonalStore.SETTINGS, 1L).data.first()[key])
            assertEquals("p2_val", stores.storeFor(PersonalStore.SETTINGS, 2L).data.first()[key])
        }

    @Test
    fun SDS3_scopedStore_dataWaitsUntilAProfileIsResolved() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(null)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)
            val key = stringPreferencesKey("wait_key")

            stores.storeFor(PersonalStore.SETTINGS, 7L).edit { prefs -> prefs[key] = "val_for_7" }

            val emissions = mutableListOf<String>()
            val job =
                launch {
                    scoped.data.collect { prefs ->
                        prefs[key]?.let { emissions.add(it) }
                    }
                }
            advanceUntilIdle()
            assertTrue(emissions.isEmpty())

            active.setActive(7L)
            advanceUntilIdle()
            assertEquals(listOf("val_for_7"), emissions)
            job.cancel()
        }

    @Test
    fun SDS4_scopedStore_updateWithoutAProfile_failsAfterFiveSeconds() =
        runTest {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(null)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)

            assertThrows(IllegalStateException::class.java) {
                runTest {
                    scoped.updateData { it }
                }
            }
        }
}
