package com.drift.stream.domain.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.drift.stream.data.local.prefs.AppStateRepositoryImpl
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.PlayerPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.local.prefs.TorrentPrefsRepositoryImpl
import com.drift.stream.data.profile.PersonalStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPrefsTest {
    @Test
    fun BP1_settingsStore_exportsOnlyPersonalKeys() {
        val prefs =
            mutablePreferencesOf(
                stringPreferencesKey("theme_mode") to "dark",
                booleanPreferencesKey("family_mode") to true,
                longPreferencesKey("last_active_profile_id") to 2L,
                booleanPreferencesKey("always_show_picker") to true,
                booleanPreferencesKey("wifi_only_torrents") to false,
            )

        val entries = BackupPrefs.exportEntries(PersonalStore.SETTINGS, prefs)

        assertEquals(2, entries.size)
        assertEquals("family_mode", entries[0].key)
        assertEquals("theme_mode", entries[1].key)
    }

    @Test
    fun BP2_homeRowsAndSourceMemory_exportEveryKey() {
        val prefs =
            mutablePreferencesOf(
                stringPreferencesKey("custom_row_1") to "row1",
                intPreferencesKey("custom_int") to 42,
            )

        val homeRows = BackupPrefs.exportEntries(PersonalStore.HOME_ROWS, prefs)
        val sourceMemory = BackupPrefs.exportEntries(PersonalStore.SOURCE_MEMORY, prefs)

        assertEquals(2, homeRows.size)
        assertEquals(2, sourceMemory.size)
        assertEquals(listOf("custom_int", "custom_row_1"), homeRows.map { it.key })
        assertEquals(listOf("custom_int", "custom_row_1"), sourceMemory.map { it.key })
    }

    @Test
    fun BP3_valueTypes_encodedWithTypeTag() {
        val prefs =
            mutablePreferencesOf(
                stringPreferencesKey("str_key") to "hello",
                intPreferencesKey("int_key") to 123,
                longPreferencesKey("long_key") to 456L,
                booleanPreferencesKey("bool_key") to true,
                floatPreferencesKey("float_key") to 1.5f,
                doublePreferencesKey("double_key") to 2.5,
                stringSetPreferencesKey("set_key") to setOf("b", "a"),
            )

        val entries = BackupPrefs.exportEntries("source_memory", prefs).associateBy { it.key }

        assertEquals(BackupPrefEntry("str_key", "string", JsonPrimitive("hello")), entries["str_key"])
        assertEquals(BackupPrefEntry("int_key", "int", JsonPrimitive(123)), entries["int_key"])
        assertEquals(BackupPrefEntry("long_key", "long", JsonPrimitive(456L)), entries["long_key"])
        assertEquals(BackupPrefEntry("bool_key", "boolean", JsonPrimitive(true)), entries["bool_key"])
        assertEquals(BackupPrefEntry("float_key", "float", JsonPrimitive(1.5f)), entries["float_key"])
        assertEquals(BackupPrefEntry("double_key", "double", JsonPrimitive(2.5)), entries["double_key"])
        assertEquals(
            BackupPrefEntry("set_key", "stringSet", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))),
            entries["set_key"],
        )
    }

    @Test
    fun BP4_entries_sortedByKey() {
        val prefs =
            mutablePreferencesOf(
                stringPreferencesKey("b") to "2",
                stringPreferencesKey("a") to "1",
                stringPreferencesKey("c") to "3",
            )

        val entries = BackupPrefs.exportEntries("source_memory", prefs)
        assertEquals(listOf("a", "b", "c"), entries.map { it.key })
    }

    @Test
    fun BP5_deviceWideKeys_neverInPersonalAllowList() {
        val deviceWideKeys =
            (AppStateRepositoryImpl.DEVICE_WIDE_KEYS + TorrentPrefsRepositoryImpl.DEVICE_WIDE_KEYS)
                .map { it.name }

        for (key in deviceWideKeys) {
            assertFalse(
                "Device-wide key '$key' must not be in personal allow-list",
                BackupPrefs.PERSONAL_SETTINGS_ALLOW_LIST.contains(key),
            )
        }
    }

    @Test
    fun BP6_everyPersonalRepositoryKey_isInAllowList() {
        val allRepositoryPersonalKeys =
            (
                SettingsRepositoryImpl.PERSONAL_KEYS +
                    PlayerPrefsRepositoryImpl.PERSONAL_KEYS +
                    ContentPrefsRepositoryImpl.PERSONAL_KEYS
            ).map { it.name }

        for (key in allRepositoryPersonalKeys) {
            assertTrue(
                "Personal key '$key' must be in personal allow-list",
                BackupPrefs.PERSONAL_SETTINGS_ALLOW_LIST.contains(key),
            )
        }
    }
}
