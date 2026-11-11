package com.drift.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppStateRepository {
    val defaultAddonsSeeded: Flow<Boolean>

    suspend fun markDefaultAddonsSeeded()

    val defaultAddonsSeededProfiles: Flow<Set<Long>>

    suspend fun markDefaultAddonsSeeded(profileId: Long)

    val lastActiveProfileId: Flow<Long?>

    suspend fun setLastActiveProfileId(id: Long)

    val alwaysShowPicker: Flow<Boolean>

    suspend fun setAlwaysShowPicker(alwaysShow: Boolean)
}
