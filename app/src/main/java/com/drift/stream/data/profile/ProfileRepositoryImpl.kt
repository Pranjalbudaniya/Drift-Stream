package com.drift.stream.data.profile

import com.drift.stream.data.local.db.NexusDatabase
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.entity.ProfileEntity
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import com.drift.stream.domain.usecase.NameResult
import com.drift.stream.domain.usecase.ProfileNameRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl
    @Inject
    constructor(
        database: NexusDatabase,
        private val transactionRunner: TransactionRunner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ProfileRepository {
        private val profileDao = database.profileDao()
        private val addonDao = database.addonDao()
        private val progressDao = database.progressDao()
        private val libraryDao = database.libraryDao()
        private val searchHistoryDao = database.searchHistoryDao()
        private val downloadDao = database.downloadDao()

        override val profiles: Flow<List<Profile>> =
            profileDao.observeAll().map { list ->
                list.map { it.toDomain() }
            }

        override suspend fun ensureDefaultProfile(): Profile =
            withContext(ioDispatcher) {
                transactionRunner.run {
                    val count = profileDao.count()
                    if (count == 0) {
                        val now = System.currentTimeMillis()
                        val entity =
                            ProfileEntity(
                                id = 1L,
                                name = "Me",
                                avatarColor = 0,
                                avatarEmoji = null,
                                isOwner = true,
                                needsSetup = true,
                                pinHash = null,
                                pinSalt = null,
                                pinIterations = null,
                                createdAt = now,
                                lastUsedAt = now,
                                sortOrder = 0,
                            )
                        profileDao.insert(entity)
                        entity.toDomain()
                    } else {
                        profileDao.getLowestId()?.toDomain()
                            ?: error("Profiles table is not empty but no lowest id found")
                    }
                }
            }

        override suspend fun get(id: Long): Profile? =
            withContext(ioDispatcher) {
                profileDao.getById(id)?.toDomain()
            }

        override suspend fun create(
            name: String,
            avatarColor: Int,
            avatarEmoji: String?,
        ): CreateResult =
            withContext(ioDispatcher) {
                val existing = profileDao.getAll()
                val existingNames = existing.map { it.name }
                when (val validation = ProfileNameRules.validate(name, existingNames)) {
                    is NameResult.Valid -> {
                        val maxSortOrder = profileDao.maxSortOrder() ?: -1
                        val now = System.currentTimeMillis()
                        val entity =
                            ProfileEntity(
                                id = 0L,
                                name = validation.trimmedName,
                                avatarColor = avatarColor,
                                avatarEmoji = avatarEmoji,
                                isOwner = false,
                                needsSetup = false,
                                pinHash = null,
                                pinSalt = null,
                                pinIterations = null,
                                createdAt = now,
                                lastUsedAt = now,
                                sortOrder = maxSortOrder + 1,
                            )
                        val generatedId = profileDao.insert(entity)
                        CreateResult.Created(entity.copy(id = generatedId).toDomain())
                    }

                    else -> {
                        CreateResult.InvalidName(validation)
                    }
                }
            }

        override suspend fun update(profile: Profile): UpdateResult =
            withContext(ioDispatcher) {
                if (profileDao.getById(profile.id) == null) {
                    return@withContext UpdateResult.NotFound
                }
                val otherNames = profileDao.getAll().filter { it.id != profile.id }.map { it.name }
                when (val validation = ProfileNameRules.validate(profile.name, otherNames)) {
                    is NameResult.Valid -> {
                        val updated = profile.copy(name = validation.trimmedName)
                        profileDao.update(updated.toEntity())
                        UpdateResult.Updated(updated)
                    }

                    else -> {
                        UpdateResult.InvalidName(validation)
                    }
                }
            }

        override suspend fun delete(id: Long): DeleteResult =
            withContext(ioDispatcher) {
                val profile = profileDao.getById(id) ?: return@withContext DeleteResult.NotFound
                if (profile.isOwner) {
                    return@withContext DeleteResult.RefusedOwner
                }
                val count = profileDao.count()
                if (count <= 1) {
                    return@withContext DeleteResult.RefusedLast
                }
                transactionRunner.run {
                    addonDao.deleteAllForProfile(id)
                    progressDao.deleteAllForProfile(id)
                    libraryDao.deleteAllForProfile(id)
                    searchHistoryDao.deleteAllForProfile(id)
                    downloadDao.deleteAllForProfile(id)
                    profileDao.deleteById(id)
                }
                DeleteResult.Deleted
            }
    }

fun ProfileEntity.toDomain(): Profile =
    Profile(
        id = id,
        name = name,
        avatarColor = avatarColor,
        avatarEmoji = avatarEmoji,
        isOwner = isOwner,
        needsSetup = needsSetup,
        pinHash = pinHash,
        pinSalt = pinSalt,
        pinIterations = pinIterations,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        sortOrder = sortOrder,
    )

fun Profile.toEntity(): ProfileEntity =
    ProfileEntity(
        id = id,
        name = name,
        avatarColor = avatarColor,
        avatarEmoji = avatarEmoji,
        isOwner = isOwner,
        needsSetup = needsSetup,
        pinHash = pinHash,
        pinSalt = pinSalt,
        pinIterations = pinIterations,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        sortOrder = sortOrder,
    )
