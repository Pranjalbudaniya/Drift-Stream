package com.drift.stream.di

import com.drift.stream.data.player.AndroidAudioFocusController
import com.drift.stream.data.player.AndroidSubtitleFileImporter
import com.drift.stream.data.player.AudioFocusController
import com.drift.stream.data.player.LibVlcPlayerFactory
import com.drift.stream.data.player.PlayerControllerFactory
import com.drift.stream.data.player.SubtitleFileImporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PlayerModule {
    @Binds
    @Singleton
    fun bindPlayerControllerFactory(impl: LibVlcPlayerFactory): PlayerControllerFactory

    @Binds
    @Singleton
    fun bindAudioFocusController(impl: AndroidAudioFocusController): AudioFocusController

    @Binds
    @Singleton
    fun bindSubtitleFileImporter(impl: AndroidSubtitleFileImporter): SubtitleFileImporter
}
