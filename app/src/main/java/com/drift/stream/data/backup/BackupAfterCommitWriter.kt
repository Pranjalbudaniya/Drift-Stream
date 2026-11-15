package com.drift.stream.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
import com.drift.stream.domain.backup.BackupPrefs
import com.drift.stream.domain.backup.ImportWarning
import com.drift.stream.domain.repository.ProfileRepository
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupAfterCommitWriter
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val profileDataStores: ProfileDataStores,
        private val avatarPhotoStore: AvatarPhotoStore,
        private val clock: Clock,
    ) {
        internal suspend fun performWrites(outcome: DbOutcome): List<ImportWarning> {
            val warnings = mutableListOf<ImportWarning>()
            writePhotos(outcome.createdPhotos, warnings)
            addSettings(outcome.settingsToAdd, warnings)
            replaceSettings(outcome.settingsToReplace, warnings)
            return warnings
        }

        private suspend fun writePhotos(
            photos: List<Pair<Long, ByteArray?>>,
            warnings: MutableList<ImportWarning>,
        ) {
            for ((profId, photoBytes) in photos) {
                if (photoBytes == null) continue
                try {
                    avatarPhotoStore.write(profId, photoBytes)
                    profileRepository.setAvatarPhoto(profId, clock.millis())
                } catch (_: IOException) {
                    warnings.add(ImportWarning.PhotoNotSaved)
                } catch (_: SecurityException) {
                    warnings.add(ImportWarning.PhotoNotSaved)
                } catch (_: IllegalStateException) {
                    warnings.add(ImportWarning.PhotoNotSaved)
                }
            }
        }

        private suspend fun addSettings(
            settings: List<Pair<Long, Map<String, Map<String, Any>>>>,
            warnings: MutableList<ImportWarning>,
        ) {
            for ((profId, settingsMap) in settings) {
                for ((storeName, values) in settingsMap) {
                    val pStore = toPersonalStore(storeName) ?: continue
                    try {
                        profileDataStores.storeFor(pStore, profId).edit { prefs ->
                            for ((k, v) in values) {
                                if (!BackupPrefs.ownsKey(pStore, k)) continue
                                putEntry(prefs, k, v)
                            }
                        }
                    } catch (_: IOException) {
                        addWarning(warnings, ImportWarning.SettingsNotFullySaved)
                    } catch (_: IllegalStateException) {
                        addWarning(warnings, ImportWarning.SettingsNotFullySaved)
                    } catch (_: IllegalArgumentException) {
                        addWarning(warnings, ImportWarning.SettingsNotFullySaved)
                    }
                }
            }
        }

        private suspend fun replaceSettings(
            settings: List<Pair<Long, Map<String, Map<String, Any>>>>,
            warnings: MutableList<ImportWarning>,
        ) {
            for ((profId, settingsMap) in settings) {
                for ((storeName, values) in settingsMap) {
                    val pStore = toPersonalStore(storeName) ?: continue
                    try {
                        profileDataStores.storeFor(pStore, profId).edit { prefs ->
                            val toRemove =
                                prefs.asMap().keys.filter { key ->
                                    BackupPrefs.ownsKey(pStore, key.name)
                                }
                            for (key in toRemove) {
                                prefs.remove(key)
                            }
                            for ((k, v) in values) {
                                if (!BackupPrefs.ownsKey(pStore, k)) continue
                                putEntry(prefs, k, v)
                            }
                        }
                    } catch (_: IOException) {
                        addWarning(warnings, ImportWarning.SettingsNotFullySaved)
                    } catch (_: IllegalStateException) {
                        addWarning(warnings, ImportWarning.SettingsNotFullySaved)
                    } catch (_: IllegalArgumentException) {
                        addWarning(warnings, ImportWarning.SettingsNotFullySaved)
                    }
                }
            }
        }

        private fun putEntry(
            prefs: MutablePreferences,
            key: String,
            value: Any,
        ) {
            when (value) {
                is Boolean -> {
                    prefs[booleanPreferencesKey(key)] = value
                }

                is Int -> {
                    prefs[intPreferencesKey(key)] = value
                }

                is Long -> {
                    prefs[longPreferencesKey(key)] = value
                }

                is Float -> {
                    prefs[floatPreferencesKey(key)] = value
                }

                is Double -> {
                    prefs[doublePreferencesKey(key)] = value
                }

                is String -> {
                    prefs[stringPreferencesKey(key)] = value
                }

                is Collection<*> -> {
                    val set = value.filterIsInstance<String>().toSet()
                    if (set.size == value.size) {
                        prefs[stringSetPreferencesKey(key)] = set
                    }
                }
            }
        }

        private fun addWarning(
            warnings: MutableList<ImportWarning>,
            warning: ImportWarning,
        ) {
            if (warning !in warnings) {
                warnings.add(warning)
            }
        }

        private fun toPersonalStore(storeName: String): PersonalStore? =
            PersonalStore.entries.firstOrNull { it.legacyFileName == storeName }
    }
