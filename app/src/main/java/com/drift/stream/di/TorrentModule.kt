package com.drift.stream.di

import com.drift.stream.data.torrent.AndroidServiceLauncher
import com.drift.stream.data.torrent.CacheLedger
import com.drift.stream.data.torrent.CacheLedgerImpl
import com.drift.stream.data.torrent.CacheRetentionCoordinator
import com.drift.stream.data.torrent.ConnectivityNetworkStatusProvider
import com.drift.stream.data.torrent.GatedTorrentEngine
import com.drift.stream.data.torrent.JLibTorrentBackend
import com.drift.stream.data.torrent.NetworkStatusProvider
import com.drift.stream.data.torrent.RefCountedKeepAlive
import com.drift.stream.data.torrent.ServiceLauncher
import com.drift.stream.data.torrent.TorrentBackend
import com.drift.stream.data.torrent.TorrentCacheFiles
import com.drift.stream.data.torrent.TorrentCacheStore
import com.drift.stream.data.torrent.TorrentFileLocator
import com.drift.stream.data.torrent.TorrentKeepAlive
import com.drift.stream.data.torrent.TorrentSessionControl
import com.drift.stream.domain.repository.CacheCompletion
import com.drift.stream.domain.repository.TorrentCache
import com.drift.stream.domain.repository.TorrentEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface TorrentModule {
    @Binds
    @Singleton
    fun bindCacheLedger(impl: CacheLedgerImpl): CacheLedger

    @Binds
    @Singleton
    fun bindCacheCompletion(impl: CacheRetentionCoordinator): CacheCompletion

    @Binds
    @Singleton
    fun bindTorrentCacheFiles(impl: TorrentCacheStore): TorrentCacheFiles

    @Binds
    @Singleton
    fun bindTorrentCache(impl: TorrentCacheStore): TorrentCache

    @Binds
    @Singleton
    fun bindTorrentBackend(impl: JLibTorrentBackend): TorrentBackend

    @Binds
    @Singleton
    fun bindTorrentFileLocator(impl: JLibTorrentBackend): TorrentFileLocator

    @Binds
    @Singleton
    fun bindTorrentEngine(impl: GatedTorrentEngine): TorrentEngine

    @Binds
    @Singleton
    fun bindTorrentSessionControl(impl: GatedTorrentEngine): TorrentSessionControl

    @Binds
    @Singleton
    fun bindTorrentKeepAlive(impl: RefCountedKeepAlive): TorrentKeepAlive

    @Binds
    @Singleton
    fun bindServiceLauncher(impl: AndroidServiceLauncher): ServiceLauncher

    @Binds
    @Singleton
    fun bindNetworkStatusProvider(impl: ConnectivityNetworkStatusProvider): NetworkStatusProvider
}
