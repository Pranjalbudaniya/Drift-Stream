package com.drift.stream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
import com.drift.stream.data.profile.ProfileScopedDataStore
import com.drift.stream.domain.repository.ActiveProfileProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PersonalSettingsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SourceMemoryDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CacheLedgerDataStore

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("settings") },
        )

    @Provides
    @Singleton
    fun provideProfileDataStores(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        legacySettings: DataStore<Preferences>,
    ): ProfileDataStores =
        ProfileDataStores(
            fileFor = { name -> context.preferencesDataStoreFile(name) },
            newScope = { CoroutineScope(ioDispatcher + SupervisorJob()) },
            legacySettings = legacySettings,
        )

    @Provides
    @Singleton
    @PersonalSettingsStore
    fun providePersonalSettingsDataStore(
        stores: ProfileDataStores,
        activeProfileProvider: ActiveProfileProvider,
    ): DataStore<Preferences> =
        ProfileScopedDataStore(
            kind = PersonalStore.SETTINGS,
            stores = stores,
            active = activeProfileProvider,
        )

    @Provides
    @Singleton
    @SourceMemoryDataStore
    fun provideSourceMemoryDataStore(
        stores: ProfileDataStores,
        activeProfileProvider: ActiveProfileProvider,
    ): DataStore<Preferences> =
        ProfileScopedDataStore(
            kind = PersonalStore.SOURCE_MEMORY,
            stores = stores,
            active = activeProfileProvider,
        )

    @Provides
    @Singleton
    @CacheLedgerDataStore
    fun provideCacheLedgerDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("cache_ledger") },
        )
}
