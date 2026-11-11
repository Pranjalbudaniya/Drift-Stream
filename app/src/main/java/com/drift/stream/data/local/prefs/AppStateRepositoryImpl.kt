package com.drift.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.drift.stream.domain.repository.AppStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AppStateRepository {
        override val defaultAddonsSeeded: Flow<Boolean> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    prefs[DEFAULT_ADDONS_SEEDED_KEY] ?: false
                }.distinctUntilChanged()

        override suspend fun markDefaultAddonsSeeded() {
            dataStore.edit { prefs ->
                prefs[DEFAULT_ADDONS_SEEDED_KEY] = true
            }
        }

        override val defaultAddonsSeededProfiles: Flow<Set<Long>> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    val stringSet = prefs[DEFAULT_ADDONS_SEEDED_PROFILES_KEY] ?: emptySet()
                    stringSet.mapNotNull { it.toLongOrNull() }.toSet()
                }.distinctUntilChanged()

        override suspend fun markDefaultAddonsSeeded(profileId: Long) {
            dataStore.edit { prefs ->
                val current = prefs[DEFAULT_ADDONS_SEEDED_PROFILES_KEY] ?: emptySet()
                prefs[DEFAULT_ADDONS_SEEDED_PROFILES_KEY] = current + profileId.toString()
            }
        }

        override val lastActiveProfileId: Flow<Long?> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    prefs[LAST_ACTIVE_PROFILE_ID_KEY]
                }.distinctUntilChanged()

        override suspend fun setLastActiveProfileId(id: Long) {
            dataStore.edit { prefs ->
                prefs[LAST_ACTIVE_PROFILE_ID_KEY] = id
            }
        }

        override val alwaysShowPicker: Flow<Boolean> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    prefs[ALWAYS_SHOW_PICKER_KEY] ?: false
                }.distinctUntilChanged()

        override suspend fun setAlwaysShowPicker(alwaysShow: Boolean) {
            dataStore.edit { prefs ->
                prefs[ALWAYS_SHOW_PICKER_KEY] = alwaysShow
            }
        }

        companion object {
            val DEFAULT_ADDONS_SEEDED_KEY = booleanPreferencesKey("default_addons_seeded")
            val DEFAULT_ADDONS_SEEDED_PROFILES_KEY = stringSetPreferencesKey("default_addons_seeded_profiles")
            val LAST_ACTIVE_PROFILE_ID_KEY = longPreferencesKey("last_active_profile_id")
            val ALWAYS_SHOW_PICKER_KEY = booleanPreferencesKey("always_show_picker")
        }
    }
