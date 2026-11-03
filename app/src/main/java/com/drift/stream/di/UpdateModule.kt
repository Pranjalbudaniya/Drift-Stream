package com.drift.stream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.drift.stream.data.update.ApkDownloaderImpl
import com.drift.stream.data.update.ApkFileStoreImpl
import com.drift.stream.data.update.ApkInstallerImpl
import com.drift.stream.data.update.DefaultUpdateManager
import com.drift.stream.data.update.GitHubReleaseSource
import com.drift.stream.data.update.NetworkInfoImpl
import com.drift.stream.data.update.PackageArchiveReaderImpl
import com.drift.stream.data.update.StorageInfoImpl
import com.drift.stream.data.update.UpdateDataStore
import com.drift.stream.data.update.UpdatePrefsRepositoryImpl
import com.drift.stream.domain.repository.ApkDownloader
import com.drift.stream.domain.repository.ApkFileStore
import com.drift.stream.domain.repository.ApkInstaller
import com.drift.stream.domain.repository.Clock
import com.drift.stream.domain.repository.NetworkInfo
import com.drift.stream.domain.repository.PackageArchiveReader
import com.drift.stream.domain.repository.ReleaseSource
import com.drift.stream.domain.repository.StorageInfo
import com.drift.stream.domain.repository.UpdateManager
import com.drift.stream.domain.repository.UpdatePrefsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface UpdateBindingModule {
    @Binds
    @Singleton
    fun bindReleaseSource(impl: GitHubReleaseSource): ReleaseSource

    @Binds
    @Singleton
    fun bindApkDownloader(impl: ApkDownloaderImpl): ApkDownloader

    @Binds
    @Singleton
    fun bindApkFileStore(impl: ApkFileStoreImpl): ApkFileStore

    @Binds
    @Singleton
    fun bindPackageArchiveReader(impl: PackageArchiveReaderImpl): PackageArchiveReader

    @Binds
    @Singleton
    fun bindApkInstaller(impl: ApkInstallerImpl): ApkInstaller

    @Binds
    @Singleton
    fun bindNetworkInfo(impl: NetworkInfoImpl): NetworkInfo

    @Binds
    @Singleton
    fun bindStorageInfo(impl: StorageInfoImpl): StorageInfo

    @Binds
    @Singleton
    fun bindUpdatePrefsRepository(impl: UpdatePrefsRepositoryImpl): UpdatePrefsRepository

    @Binds
    @Singleton
    fun bindUpdateManager(impl: DefaultUpdateManager): UpdateManager
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    @UpdateDataStore
    fun provideUpdateDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("update_prefs") },
        )

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
