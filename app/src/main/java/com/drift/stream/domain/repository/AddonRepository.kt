package com.drift.stream.domain.repository

import com.drift.stream.domain.model.Addon
import com.drift.stream.domain.model.AddonManifest
import com.drift.stream.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface AddonRepository {
    fun observeAll(): Flow<List<Addon>>

    fun observeEnabled(): Flow<List<Addon>>

    fun observeActive(): Flow<List<Addon>>

    fun observeHiddenSharedCount(): Flow<Int>

    suspend fun install(transportUrl: String): Outcome<Addon>

    suspend fun refresh(transportUrl: String): Outcome<Addon>

    suspend fun fetchManifest(transportUrl: String): Outcome<AddonManifest>

    suspend fun remove(transportUrl: String)

    suspend fun setEnabled(
        transportUrl: String,
        enabled: Boolean,
    )

    suspend fun move(
        transportUrl: String,
        toPosition: Int,
    )

    suspend fun setShared(
        transportUrl: String,
        shared: Boolean,
    ): Outcome<Unit>

    suspend fun setSharedEnabled(
        ownerProfileId: Long,
        transportUrl: String,
        enabled: Boolean,
    )
}
