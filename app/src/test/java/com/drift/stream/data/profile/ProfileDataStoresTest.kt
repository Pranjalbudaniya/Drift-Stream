package com.drift.stream.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDataStoresTest {
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
    fun PDS1_profileOne_settings_isTheLegacySingleton() =
        runTest(testDispatcher) {
            val (stores, legacySettings) = createStores()
            val store = stores.storeFor(PersonalStore.SETTINGS, 1L)

            assertSame(legacySettings, store)

            val key = stringPreferencesKey("test_key")
            store.edit { prefs -> prefs[key] = "hello_legacy" }

            val readFromLegacy = legacySettings.data.first()[key]
            assertEquals("hello_legacy", readFromLegacy)
        }

    @Test
    fun PDS2_otherProfile_hasItsOwnFile_andIsIsolated() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val p1Store = stores.storeFor(PersonalStore.SETTINGS, 1L)
            val p2Store = stores.storeFor(PersonalStore.SETTINGS, 2L)

            assertNotEquals(p1Store, p2Store)

            val key = stringPreferencesKey("isolated_key")
            p2Store.edit { prefs -> prefs[key] = "profile_two_val" }

            assertEquals("profile_two_val", p2Store.data.first()[key])
            assertNull(p1Store.data.first()[key])

            val p2File = File(tmpFolder.root, "settings_p2.preferences_pb")
            assertTrue(p2File.exists())
            assertTrue(p2File.name.endsWith("_p2.preferences_pb"))
        }

    @Test
    fun PDS3_sameKindAndProfile_returnsTheSameInstance() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val firstInstance = stores.storeFor(PersonalStore.SETTINGS, 2L)
            val secondInstance = stores.storeFor(PersonalStore.SETTINGS, 2L)

            assertSame(firstInstance, secondInstance)
        }

    @Test
    fun PDS4_delete_removesTheFilesAndTheCacheEntry() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val p2Store = stores.storeFor(PersonalStore.SETTINGS, 2L)
            val key = stringPreferencesKey("delete_test")
            p2Store.edit { prefs -> prefs[key] = "to_be_deleted" }

            val p2File = File(tmpFolder.root, "settings_p2.preferences_pb")
            assertTrue(p2File.exists())

            stores.delete(2L)

            assertFalse(p2File.exists())

            val freshStore = stores.storeFor(PersonalStore.SETTINGS, 2L)
            assertNull(freshStore.data.first()[key])
        }
}
