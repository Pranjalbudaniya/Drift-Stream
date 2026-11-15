package com.drift.stream.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
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
            clearStores(outcome.storesToClear, warnings)
            writeSettings(outcome.settingsToWrite, warnings)
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

        private suspend fun clearStores(
            clears: List<Pair<Long, Set<String>>>,
            warnings: MutableList<ImportWarning>,
        ) {
            for ((profId, storeNames) in clears) {
                for (storeName in storeNames) {
                    val pStore = toPersonalStore(storeName) ?: continue
                    try {
                        profileDataStores.storeFor(pStore, profId).edit { it.clear() }
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

        private suspend fun writeSettings(
            settings: List<Pair<Long, Map<String, Map<String, Any>>>>,
            warnings: MutableList<ImportWarning>,
        ) {
            for ((profId, settingsMap) in settings) {
                for ((storeName, values) in settingsMap) {
                    val pStore = toPersonalStore(storeName) ?: continue
                    try {
                        writeStoreValues(pStore, profId, values)
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

        private fun addWarning(
            warnings: MutableList<ImportWarning>,
            warning: ImportWarning,
        ) {
            if (warning !in warnings) {
                warnings.add(warning)
            }
        }

        private suspend fun writeStoreValues(
            store: PersonalStore,
            profileId: Long,
            values: Map<String, Any>,
        ) {
            profileDataStores.storeFor(store, profileId).edit { prefs ->
                for ((k, v) in values) {
                    when (v) {
                        is Boolean -> {
                            prefs[booleanPreferencesKey(k)] = v
                        }

                        is Int -> {
                            prefs[intPreferencesKey(k)] = v
                        }

                        is Long -> {
                            prefs[longPreferencesKey(k)] = v
                        }

                        is Float -> {
                            prefs[floatPreferencesKey(k)] = v
                        }

                        is Double -> {
                            prefs[floatPreferencesKey(k)] = v.toFloat()
                        }

                        is String -> {
                            prefs[stringPreferencesKey(k)] = v
                        }

                        is Collection<*> -> {
                            val set = v.filterIsInstance<String>().toSet()
                            prefs[stringSetPreferencesKey(k)] = set
                        }
                    }
                }
            }
        }

        private fun toPersonalStore(storeName: String): PersonalStore? =
            PersonalStore.entries.firstOrNull { it.legacyFileName == storeName }
    }
