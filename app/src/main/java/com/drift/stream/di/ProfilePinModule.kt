package com.drift.stream.di

import com.drift.stream.data.profile.AndroidAppDataEraser
import com.drift.stream.data.profile.DefaultPinChallenges
import com.drift.stream.data.profile.PinHasher
import com.drift.stream.data.profile.ProfilePinRepositoryImpl
import com.drift.stream.domain.profile.AppDataEraser
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.repository.ProfilePinRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ProfilePinModule {
    @Binds
    @Singleton
    fun bindProfilePinRepository(impl: ProfilePinRepositoryImpl): ProfilePinRepository

    @Binds
    @Singleton
    fun bindPinChallenges(impl: DefaultPinChallenges): PinChallenges

    @Binds
    @Singleton
    fun bindAppDataEraser(impl: AndroidAppDataEraser): AppDataEraser

    companion object {
        @Provides
        @Singleton
        fun providePinHasher(
            @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): PinHasher = PinHasher(defaultDispatcher = defaultDispatcher)
    }
}
