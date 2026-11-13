package com.drift.stream.di

import com.drift.stream.data.profile.PinHasher
import com.drift.stream.data.profile.ProfilePinRepositoryImpl
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

    companion object {
        @Provides
        @Singleton
        fun providePinHasher(
            @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        ): PinHasher = PinHasher(defaultDispatcher = defaultDispatcher)
    }
}
