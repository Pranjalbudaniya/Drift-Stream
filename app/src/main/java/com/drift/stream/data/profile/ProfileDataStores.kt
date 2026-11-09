package com.drift.stream.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.io.File

enum class PersonalStore(
    val legacyFileName: String,
) {
    SETTINGS("settings"),
    SOURCE_MEMORY("source_memory"),
    HOME_ROWS("home_rows"),
}

class ProfileDataStores(
    private val fileFor: (String) -> File,
    private val newScope: () -> CoroutineScope,
    private val legacySettings: DataStore<Preferences>,
) {
    private data class StoreEntry(
        val store: DataStore<Preferences>,
        val scope: CoroutineScope?,
    )

    private val lock = Any()
    private val cache = mutableMapOf<Pair<PersonalStore, Long>, StoreEntry>()

    fun storeFor(
        kind: PersonalStore,
        profileId: Long,
    ): DataStore<Preferences> =
        if (kind == PersonalStore.SETTINGS && profileId == 1L) {
            legacySettings
        } else {
            synchronized(lock) {
                cache
                    .getOrPut(kind to profileId) {
                        val scope = newScope()
                        val fileName =
                            if (profileId == 1L) {
                                kind.legacyFileName
                            } else {
                                "${kind.legacyFileName}_p$profileId"
                            }
                        val file = fileFor(fileName)
                        val store =
                            PreferenceDataStoreFactory.create(
                                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                                scope = scope,
                                produceFile = { file },
                            )
                        StoreEntry(store, scope)
                    }.store
            }
        }

    fun delete(profileId: Long) {
        if (profileId == 1L) return
        val toCancel = mutableListOf<CoroutineScope>()
        synchronized(lock) {
            for (kind in PersonalStore.entries) {
                val key = kind to profileId
                val entry = cache.remove(key)
                if (entry?.scope != null) {
                    toCancel.add(entry.scope)
                }
            }
        }
        for (scope in toCancel) {
            scope.cancel()
        }
        for (kind in PersonalStore.entries) {
            val fileName = "${kind.legacyFileName}_p$profileId"
            deleteFileAndTemp(fileFor(fileName))
        }
    }

    private fun deleteFileAndTemp(file: File) {
        if (file.exists()) {
            file.delete()
        }
        val parent = file.parentFile
        if (parent != null) {
            val tmp = File(parent, "${file.name}.tmp")
            if (tmp.exists()) {
                tmp.delete()
            }
        }
    }
}
