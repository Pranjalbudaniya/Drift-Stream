package com.drift.stream.domain.backup

import androidx.datastore.preferences.core.Preferences
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.PlayerPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.profile.PersonalStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object BackupPrefs {
    val PERSONAL_SETTINGS_ALLOW_LIST: Set<String> =
        (
            SettingsRepositoryImpl.PERSONAL_KEYS.map { it.name } +
                PlayerPrefsRepositoryImpl.PERSONAL_KEYS.map { it.name } +
                ContentPrefsRepositoryImpl.PERSONAL_KEYS.map { it.name }
        ).toSet()

    fun exportEntries(
        store: PersonalStore,
        prefs: Preferences,
    ): List<BackupPrefEntry> = exportEntries(store.legacyFileName, prefs)

    fun exportEntries(
        storeName: String,
        prefs: Preferences,
    ): List<BackupPrefEntry> {
        val isSettings = storeName.equals(PersonalStore.SETTINGS.legacyFileName, ignoreCase = true)
        return prefs
            .asMap()
            .entries
            .filter { (key, _) -> !isSettings || key.name in PERSONAL_SETTINGS_ALLOW_LIST }
            .mapNotNull { (key, value) -> encodeEntry(key.name, value) }
            .sortedBy { it.key }
    }

    private fun encodeEntry(
        keyName: String,
        value: Any,
    ): BackupPrefEntry? {
        val (type, jsonElement) =
            when (value) {
                is String -> {
                    "string" to JsonPrimitive(value)
                }

                is Boolean -> {
                    "boolean" to JsonPrimitive(value)
                }

                is Int -> {
                    "int" to JsonPrimitive(value)
                }

                is Long -> {
                    "long" to JsonPrimitive(value)
                }

                is Float -> {
                    "float" to JsonPrimitive(value)
                }

                is Double -> {
                    "double" to JsonPrimitive(value)
                }

                is Set<*> -> {
                    val stringItems = value.filterIsInstance<String>().sorted()
                    "stringSet" to JsonArray(stringItems.map { JsonPrimitive(it) })
                }

                else -> {
                    return null
                }
            }
        return BackupPrefEntry(key = keyName, type = type, value = jsonElement)
    }
}

fun exportEntries(
    store: PersonalStore,
    prefs: Preferences,
): List<BackupPrefEntry> = BackupPrefs.exportEntries(store, prefs)

fun exportEntries(
    storeName: String,
    prefs: Preferences,
): List<BackupPrefEntry> = BackupPrefs.exportEntries(storeName, prefs)
