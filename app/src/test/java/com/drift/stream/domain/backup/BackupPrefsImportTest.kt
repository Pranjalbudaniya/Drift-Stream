package com.drift.stream.domain.backup

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.PlayerPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.profile.PersonalStore
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPrefsImportTest {
    @Test
    fun BI1_personalKeyTypes_coverExactlyThePersonalKeys() {
        assertEquals(
            SettingsRepositoryImpl.PERSONAL_KEYS.map { it.name }.toSet(),
            SettingsRepositoryImpl.PERSONAL_KEY_TYPES.keys,
        )
        assertEquals(
            PlayerPrefsRepositoryImpl.PERSONAL_KEYS.map { it.name }.toSet(),
            PlayerPrefsRepositoryImpl.PERSONAL_KEY_TYPES.keys,
        )
        assertEquals(
            ContentPrefsRepositoryImpl.PERSONAL_KEYS.map { it.name }.toSet(),
            ContentPrefsRepositoryImpl.PERSONAL_KEY_TYPES.keys,
        )
    }

    @Test
    fun BI2_settingsEntries_typed_applied() {
        val entries =
            listOf(
                BackupPrefEntry(key = "theme_mode", type = "string", value = JsonPrimitive("dark")),
                BackupPrefEntry(key = "amoled", type = "boolean", value = JsonPrimitive(true)),
                BackupPrefEntry(key = "accent_color", type = "long", value = JsonPrimitive(4294967295L)),
            )

        val result = BackupPrefs.toTypedValues(PersonalStore.SETTINGS, entries)

        assertEquals("dark", result.values["theme_mode"])
        assertEquals(true, result.values["amoled"])
        assertEquals(4294967295L, result.values["accent_color"])
        assertEquals(0, result.skipped)
    }

    @Test
    fun BI3_homeRowsAndSourceMemory_acceptedByStoreRules() {
        val smEntries =
            listOf(
                BackupPrefEntry(key = "src.1", type = "string", value = JsonPrimitive("{\"source\":\"torrent\"}")),
            )
        val smResult = BackupPrefs.toTypedValues(PersonalStore.SOURCE_MEMORY, smEntries)
        assertEquals("{\"source\":\"torrent\"}", smResult.values["src.1"])
        assertEquals(0, smResult.skipped)

        val hrEntries =
            listOf(
                BackupPrefEntry(key = "home_row_config", type = "string", value = JsonPrimitive("{\"rows\":[]}")),
            )
        val hrResult = BackupPrefs.toTypedValues(PersonalStore.HOME_ROWS, hrEntries)
        assertEquals("{\"rows\":[]}", hrResult.values["home_row_config"])
        assertEquals(0, hrResult.skipped)
    }

    @Test
    fun BI4_homeRowsAndSourceMemory_wrongType_skipped() {
        val smWrongType =
            listOf(
                BackupPrefEntry(key = "src.1", type = "int", value = JsonPrimitive(42)),
                BackupPrefEntry(key = "other_key", type = "string", value = JsonPrimitive("val")),
            )
        val smResult = BackupPrefs.toTypedValues(PersonalStore.SOURCE_MEMORY, smWrongType)
        assertTrue(smResult.values.isEmpty())
        assertEquals(2, smResult.skipped)

        val hrWrongType =
            listOf(
                BackupPrefEntry(key = "home_row_config", type = "int", value = JsonPrimitive(42)),
                BackupPrefEntry(key = "other_key", type = "string", value = JsonPrimitive("val")),
            )
        val hrResult = BackupPrefs.toTypedValues(PersonalStore.HOME_ROWS, hrWrongType)
        assertTrue(hrResult.values.isEmpty())
        assertEquals(2, hrResult.skipped)
    }

    @Test
    fun BI5_stringSet_roundTrips() {
        val originalSet = setOf("alpha", "beta", "gamma")
        val prefs = mutablePreferencesOf(stringSetPreferencesKey("sample_set") to originalSet)

        val exported = BackupPrefs.exportEntries("source_memory", prefs)
        val imported = BackupPrefs.toTypedValues("custom_store", exported, mapOf("sample_set" to "stringSet"))

        assertEquals(originalSet, imported.values["sample_set"])
        assertEquals(0, imported.skipped)
    }
}
