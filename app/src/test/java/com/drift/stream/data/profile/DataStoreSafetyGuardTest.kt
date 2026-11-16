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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSafetyGuardTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun resolveMainSourceDirectory(): File {
        val candidates =
            listOf(
                File("src/main/java"),
                File("app/src/main/java"),
                File(System.getProperty("user.dir"), "src/main/java"),
                File(System.getProperty("user.dir"), "app/src/main/java"),
            )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot find main source directory in: $candidates (user.dir=${System.getProperty("user.dir")})")
    }

    @Test
    fun SG1_noWholeStoreClear_inMainSources() {
        val storeClearRegex =
            Regex(
                """\.edit\s*\{[^{}]*?\b(?:\w+\.)?clear\(\)|\b(?:preferences|prefs|mutablePreferences)\.clear\(\)""",
                RegexOption.DOT_MATCHES_ALL,
            )

        // Prove the scanner flags the P22b-style snippet and other whole-store clear patterns
        val p22bSnippet = "profileDataStores.storeFor(pStore, profId).edit { it.clear() }"
        assertTrue("Regex must flag P22b snippet", storeClearRegex.containsMatchIn(p22bSnippet))
        assertTrue(
            "Regex must flag edit block clear",
            storeClearRegex.containsMatchIn("store.edit { prefs -> prefs.clear() }"),
        )
        assertTrue(
            "Regex must flag preferences.clear()",
            storeClearRegex.containsMatchIn("preferences.clear()"),
        )

        val allowList = emptyMap<String, String>()
        val mainSrcDir = resolveMainSourceDirectory()
        val violations = mutableListOf<String>()

        mainSrcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val relativePath = file.relativeTo(mainSrcDir).path
            if (relativePath in allowList) return@forEach
            val text = file.readText()
            if (storeClearRegex.containsMatchIn(text)) {
                violations.add(relativePath)
            }
        }

        assertTrue("Forbidden whole-store clear() found in: $violations", violations.isEmpty())
    }

    @Test
    fun SG2_deleteStores_forLegacyProfile_isRefused() =
        runTest(testDispatcher) {
            val legacyFile = File(tmpFolder.root, "settings.preferences_pb")
            val legacySettings: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(testDispatcher),
                    produceFile = { legacyFile },
                )
            val legacyDeviceKey = stringPreferencesKey("device_wide_setting")
            legacySettings.edit { prefs ->
                prefs[legacyDeviceKey] = "preserved_value"
            }

            val stores =
                ProfileDataStores(
                    fileFor = { name -> File(tmpFolder.root, "$name.preferences_pb") },
                    newScope = { CoroutineScope(testDispatcher) },
                    legacySettings = legacySettings,
                )

            val p2Store = stores.storeFor(PersonalStore.SETTINGS, 2L)
            val p2Key = stringPreferencesKey("p2_setting")
            p2Store.edit { prefs ->
                prefs[p2Key] = "p2_value"
            }

            val p2File = File(tmpFolder.root, "settings_p2.preferences_pb")
            assertTrue("Expected p2 file to exist", p2File.exists())
            assertTrue("Expected legacy file to exist", legacyFile.exists())

            stores.delete(ProfileDataStores.LEGACY_OWNER_PROFILE_ID)

            assertTrue("Legacy settings file must not be deleted", legacyFile.exists())
            val legacyVal = legacySettings.data.first()[legacyDeviceKey]
            assertEquals("preserved_value", legacyVal)

            stores.delete(2L)
            assertFalse("Non-legacy profile file should be deleted", p2File.exists())
        }
}
