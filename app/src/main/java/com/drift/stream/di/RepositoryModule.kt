package com.drift.stream.di

import com.drift.stream.data.local.prefs.AppStateRepositoryImpl
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.PlayerPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.local.prefs.TorrentPrefsRepositoryImpl
import com.drift.stream.data.repository.AddonRepositoryImpl
import com.drift.stream.data.repository.CommunityAddonsRepositoryImpl
import com.drift.stream.data.repository.LibraryRepositoryImpl
import com.drift.stream.data.repository.MetaRepositoryImpl
import com.drift.stream.data.repository.ProgressRepositoryImpl
import com.drift.stream.data.repository.SearchHistoryRepositoryImpl
import com.drift.stream.data.repository.StreamRepositoryImpl
import com.drift.stream.domain.model.PlaybackCapabilities
import com.drift.stream.domain.repository.AddonRepository
import com.drift.stream.domain.repository.AppStateRepository
import com.drift.stream.domain.repository.CommunityAddonsRepository
import com.drift.stream.domain.repository.ContentPrefsRepository
import com.drift.stream.domain.repository.LibraryRepository
import com.drift.stream.domain.repository.MetaRepository
import com.drift.stream.domain.repository.PlayerPrefsRepository
import com.drift.stream.domain.repository.ProgressRepository
import com.drift.stream.domain.repository.SearchHistoryRepository
import com.drift.stream.domain.repository.SettingsRepository
import com.drift.stream.domain.repository.StreamRepository
import com.drift.stream.domain.repository.TorrentPrefsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    fun bindAddonRepository(impl: AddonRepositoryImpl): AddonRepository

    @Binds
    @Singleton
    fun bindAppStateRepository(impl: AppStateRepositoryImpl): AppStateRepository

    @Binds
    @Singleton
    fun bindContentPrefsRepository(impl: ContentPrefsRepositoryImpl): ContentPrefsRepository

    @Binds
    @Singleton
    fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    fun bindMetaRepository(impl: MetaRepositoryImpl): MetaRepository

    @Binds
    @Singleton
    fun bindCommunityAddonsRepository(impl: CommunityAddonsRepositoryImpl): CommunityAddonsRepository

    @Binds
    @Singleton
    fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds
    @Singleton
    fun bindPlayerPrefsRepository(impl: PlayerPrefsRepositoryImpl): PlayerPrefsRepository

    @Binds
    @Singleton
    fun bindTorrentPrefsRepository(impl: TorrentPrefsRepositoryImpl): TorrentPrefsRepository

    @Binds
    @Singleton
    fun bindSearchHistoryRepository(impl: SearchHistoryRepositoryImpl): SearchHistoryRepository

    companion object {
        @Provides
        @Singleton
        fun providePlaybackCapabilities(): PlaybackCapabilities = PlaybackCapabilities(supportsTorrent = true)
    }
}
