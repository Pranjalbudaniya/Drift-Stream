package com.drift.stream.domain.backup

import androidx.datastore.preferences.core.Preferences
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.PlayerPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.profile.PersonalStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val MAX_STRING_LENGTH = 64 * 1024 // 64 KB
private const val MAX_SET_SIZE = 5_000
private const val MAX_STRING_IN_SET_LENGTH = 1_000

data class TypedValuesResult(
    val values: Map<String, Any>,
    val skipped: Int,
)

object BackupPrefs {
    val PERSONAL_SETTINGS_ALLOW_LIST: Set<String> =
        (
            SettingsRepositoryImpl.PERSONAL_KEYS.map { it.name } +
                PlayerPrefsRepositoryImpl.PERSONAL_KEYS.map { it.name } +
                ContentPrefsRepositoryImpl.PERSONAL_KEYS.map { it.name }
        ).toSet()

    val PERSONAL_KEY_TYPES: Map<String, String> =
        SettingsRepositoryImpl.PERSONAL_KEY_TYPES +
            PlayerPrefsRepositoryImpl.PERSONAL_KEY_TYPES +
            ContentPrefsRepositoryImpl.PERSONAL_KEY_TYPES

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

    fun toTypedValues(
        store: PersonalStore,
        entries: List<BackupPrefEntry>,
        keyTypes: Map<String, String> = PERSONAL_KEY_TYPES,
    ): TypedValuesResult = toTypedValues(store.legacyFileName, entries, keyTypes)

    fun toTypedValues(
        storeName: String,
        entries: List<BackupPrefEntry>,
        keyTypes: Map<String, String> = PERSONAL_KEY_TYPES,
    ): TypedValuesResult =
        when {
            storeName.equals(PersonalStore.SOURCE_MEMORY.legacyFileName, ignoreCase = true) -> {
                parseSourceMemoryEntries(entries)
            }

            storeName.equals(PersonalStore.HOME_ROWS.legacyFileName, ignoreCase = true) -> {
                parseHomeRowsEntries(entries)
            }

            else -> {
                parseTypedEntries(entries, keyTypes)
            }
        }
}

private fun parseSourceMemoryEntries(entries: List<BackupPrefEntry>): TypedValuesResult {
    val values = mutableMapOf<String, Any>()
    var skipped = 0
    for (entry in entries) {
        val accepted =
            if (entry.key.startsWith("src.") && entry.type == "string") {
                parseAndValidateValue("string", entry.value)
            } else {
                null
            }
        if (accepted != null) {
            values[entry.key] = accepted
        } else {
            skipped++
        }
    }
    return TypedValuesResult(values, skipped)
}

private fun parseHomeRowsEntries(entries: List<BackupPrefEntry>): TypedValuesResult {
    val values = mutableMapOf<String, Any>()
    var skipped = 0
    for (entry in entries) {
        val accepted =
            if (entry.key == "home_row_config" && entry.type == "string") {
                parseAndValidateValue("string", entry.value)
            } else {
                null
            }
        if (accepted != null) {
            values[entry.key] = accepted
        } else {
            skipped++
        }
    }
    return TypedValuesResult(values, skipped)
}

private fun parseTypedEntries(
    entries: List<BackupPrefEntry>,
    keyTypes: Map<String, String>,
): TypedValuesResult {
    val values = mutableMapOf<String, Any>()
    var skipped = 0
    for (entry in entries) {
        val expectedTag = keyTypes[entry.key]
        val accepted =
            if (expectedTag != null && entry.type == expectedTag) {
                parseAndValidateValue(expectedTag, entry.value)
            } else {
                null
            }
        if (accepted != null) {
            values[entry.key] = accepted
        } else {
            skipped++
        }
    }
    return TypedValuesResult(values, skipped)
}

private fun parseAndValidateValue(
    expectedTag: String,
    element: JsonElement,
): Any? {
    val primitive = element as? JsonPrimitive
    return when (expectedTag) {
        "string" -> primitive?.takeIf { it.isString && it.content.length <= MAX_STRING_LENGTH }?.content
        "boolean" -> primitive?.takeUnless { it.isString }?.booleanOrNull
        "int" -> primitive?.takeUnless { it.isString }?.intOrNull
        "long" -> primitive?.takeUnless { it.isString }?.longOrNull
        "float" -> primitive?.takeUnless { it.isString }?.floatOrNull
        "double" -> primitive?.takeUnless { it.isString }?.doubleOrNull
        "stringSet" -> parseStringSet(element)
        else -> null
    }
}

private fun parseStringSet(element: JsonElement): Set<String>? {
    val array = element as? JsonArray
    return if (array != null && array.size <= MAX_SET_SIZE && isValidStringSet(array)) {
        array.map { (it as JsonPrimitive).content }.toSet()
    } else {
        null
    }
}

private fun isValidStringSet(array: JsonArray): Boolean =
    array.all { item ->
        (item as? JsonPrimitive)?.let { p ->
            p.isString && p.content.length <= MAX_STRING_IN_SET_LENGTH
        } == true
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
