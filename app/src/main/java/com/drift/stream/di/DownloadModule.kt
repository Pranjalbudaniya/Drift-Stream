package com.drift.stream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.drift.stream.data.download.DefaultDownloadManager
import com.drift.stream.data.download.DownloadClockImpl
import com.drift.stream.data.download.DownloadDataStore
import com.drift.stream.data.download.DownloadFileStoreImpl
import com.drift.stream.data.download.DownloadNetworkImpl
import com.drift.stream.data.download.DownloadNotifierImpl
import com.drift.stream.data.download.DownloadPrefsRepositoryImpl
import com.drift.stream.data.download.DownloadRepositoryImpl
import com.drift.stream.data.download.DownloadServiceControllerImpl
import com.drift.stream.data.download.HttpDownloaderImpl
import com.drift.stream.data.download.MediaStoreGalleryExporter
import com.drift.stream.data.download.TorrentDownloaderImpl
import com.drift.stream.data.torrent.JLibTorrentBackend
import com.drift.stream.data.torrent.TorrentDownloadBackend
import com.drift.stream.domain.repository.DownloadClock
import com.drift.stream.domain.repository.DownloadFileStore
import com.drift.stream.domain.repository.DownloadManager
import com.drift.stream.domain.repository.DownloadNetwork
import com.drift.stream.domain.repository.DownloadNotifier
import com.drift.stream.domain.repository.DownloadPlatformComponents
import com.drift.stream.domain.repository.DownloadPrefsRepository
import com.drift.stream.domain.repository.DownloadRepository
import com.drift.stream.domain.repository.DownloadServiceController
import com.drift.stream.domain.repository.GalleryExporter
import com.drift.stream.domain.repository.HttpDownloader
import com.drift.stream.domain.repository.StorageInfo
import com.drift.stream.domain.repository.TorrentDownloader
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
interface DownloadBindingModule {
    @Binds
    @Singleton
    fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    fun bindDownloadPrefsRepository(impl: DownloadPrefsRepositoryImpl): DownloadPrefsRepository

    @Binds
    @Singleton
    fun bindHttpDownloader(impl: HttpDownloaderImpl): HttpDownloader

    @Binds
    @Singleton
    fun bindTorrentDownloader(impl: TorrentDownloaderImpl): TorrentDownloader

    @Binds
    @Singleton
    fun bindTorrentDownloadBackend(impl: JLibTorrentBackend): TorrentDownloadBackend

    @Binds
    @Singleton
    fun bindDownloadFileStore(impl: DownloadFileStoreImpl): DownloadFileStore

    @Binds
    @Singleton
    fun bindDownloadNetwork(impl: DownloadNetworkImpl): DownloadNetwork

    @Binds
    @Singleton
    fun bindDownloadNotifier(impl: DownloadNotifierImpl): DownloadNotifier

    @Binds
    @Singleton
    fun bindDownloadServiceController(impl: DownloadServiceControllerImpl): DownloadServiceController

    @Binds
    @Singleton
    fun bindDownloadClock(impl: DownloadClockImpl): DownloadClock

    @Binds
    @Singleton
    fun bindDownloadManager(impl: DefaultDownloadManager): DownloadManager
}

@Module
@InstallIn(SingletonComponent::class)
interface GalleryBindingModule {
    @Binds
    @Singleton
    fun bindGalleryExporter(impl: MediaStoreGalleryExporter): GalleryExporter
}

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    @DownloadDataStore
    fun provideDownloadDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("download_prefs") },
        )
}
