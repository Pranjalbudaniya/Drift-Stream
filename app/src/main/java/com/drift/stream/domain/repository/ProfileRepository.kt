package com.drift.stream.domain.repository

import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.usecase.NameResult
import kotlinx.coroutines.flow.Flow

sealed interface CreateResult {
    data class Created(
        val profile: Profile,
    ) : CreateResult

    data class InvalidName(
        val reason: NameResult,
    ) : CreateResult
}

sealed interface UpdateResult {
    data class Updated(
        val profile: Profile,
    ) : UpdateResult

    data class InvalidName(
        val reason: NameResult,
    ) : UpdateResult

    data object NotFound : UpdateResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    data object RefusedOwner : DeleteResult

    data object RefusedLast : DeleteResult

    data object RefusedActive : DeleteResult

    data object NotFound : DeleteResult
}

interface ProfileRepository {
    val profiles: Flow<List<Profile>>

    suspend fun ensureDefaultProfile(): Profile

    suspend fun get(id: Long): Profile?

    suspend fun create(
        name: String,
        avatarColor: Int,
        avatarEmoji: String?,
    ): CreateResult

    suspend fun update(profile: Profile): UpdateResult

    suspend fun completeSetup(
        id: Long,
        name: String,
        avatarColor: Int,
        avatarEmoji: String?,
    ): UpdateResult

    suspend fun delete(id: Long): DeleteResult
}
