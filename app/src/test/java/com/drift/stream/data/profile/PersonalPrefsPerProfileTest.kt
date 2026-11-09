package com.drift.stream.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.PlayerPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.repository.HomeRowConfigRepositoryImpl
import com.drift.stream.data.repository.SourceMemoryRepositoryImpl
import com.drift.stream.domain.model.HomeRowConfig
import com.drift.stream.domain.model.SourceFingerprint
import com.drift.stream.domain.model.ThemeMode
import com.drift.stream.testutil.FakeActiveProfileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalPrefsPerProfileTest {
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
    fun PDR1_playerPrefs_arePerProfile() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(1L)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)
            val repo = PlayerPrefsRepositoryImpl(scoped)

            assertTrue(repo.autoPlayNext.first())

            active.setActive(2L)
            repo.setAutoPlayNext(false)
            assertFalse(repo.autoPlayNext.first())

            active.setActive(1L)
            assertTrue(repo.autoPlayNext.first())
        }

    @Test
    fun PDR2_familyMode_isPerProfile() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(1L)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)
            val repo = ContentPrefsRepositoryImpl(scoped)

            assertTrue(repo.familyMode.first())

            active.setActive(2L)
            repo.setFamilyMode(false)
            assertFalse(repo.familyMode.first())

            active.setActive(1L)
            assertTrue(repo.familyMode.first())

            active.setActive(2L)
            assertFalse(repo.familyMode.first())
        }

    @Test
    fun PDR3_themeAndAccent_arePerProfile() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(1L)
            val scoped = ProfileScopedDataStore(PersonalStore.SETTINGS, stores, active)
            val repo = SettingsRepositoryImpl(scoped)

            val color1 = 0xFF00FF00L
            val color2 = 0xFFFF0000L

            repo.setThemeMode(ThemeMode.DARK)
            repo.setAccentColor(color1)
            assertEquals(ThemeMode.DARK, repo.themeSettings.first().mode)
            assertEquals(color1, repo.themeSettings.first().accentColor)

            active.setActive(2L)
            assertEquals(ThemeMode.SYSTEM, repo.themeSettings.first().mode)
            assertNull(repo.themeSettings.first().accentColor)

            repo.setThemeMode(ThemeMode.LIGHT)
            repo.setAccentColor(color2)
            assertEquals(ThemeMode.LIGHT, repo.themeSettings.first().mode)
            assertEquals(color2, repo.themeSettings.first().accentColor)

            active.setActive(1L)
            assertEquals(ThemeMode.DARK, repo.themeSettings.first().mode)
            assertEquals(color1, repo.themeSettings.first().accentColor)
        }

    @Test
    fun PDR4_homeRowConfig_isPerProfile() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(1L)
            val scoped = ProfileScopedDataStore(PersonalStore.HOME_ROWS, stores, active)
            val repo = HomeRowConfigRepositoryImpl(scoped)

            active.setActive(2L)
            repo.update {
                HomeRowConfig(
                    order = listOf("custom_row_1", "custom_row_2"),
                    hidden = setOf("hidden_row"),
                )
            }
            val p2Config = repo.config.first()
            assertEquals(listOf("custom_row_1", "custom_row_2"), p2Config.order)
            assertEquals(setOf("hidden_row"), p2Config.hidden)

            active.setActive(1L)
            val p1Config = repo.config.first()
            assertTrue(p1Config.order.isEmpty())
            assertTrue(p1Config.hidden.isEmpty())
        }

    @Test
    fun PDR5_sourceMemory_isPerProfile() =
        runTest(testDispatcher) {
            val (stores, _) = createStores()
            val active = FakeActiveProfileProvider(1L)
            val scoped = ProfileScopedDataStore(PersonalStore.SOURCE_MEMORY, stores, active)
            val repo = SourceMemoryRepositoryImpl(scoped) { 1000L }

            val fp =
                SourceFingerprint(
                    addonKeyHashes = listOf("hash1"),
                    kind = "torrent",
                    infoHash = "ih1",
                )

            active.setActive(2L)
            repo.remember("key1", fp)
            assertEquals(fp, repo.get("key1"))

            active.setActive(1L)
            assertNull(repo.get("key1"))
        }
}
