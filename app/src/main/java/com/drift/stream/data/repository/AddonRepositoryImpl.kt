package com.drift.stream.data.repository

import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.ProfileAddonPrefsDao
import com.drift.stream.data.local.db.dao.SharedAddonRow
import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.ProfileAddonPrefEntity
import com.drift.stream.data.profile.requireId
import com.drift.stream.data.profile.scopedFlow
import com.drift.stream.data.remote.addon.AddonMappers
import com.drift.stream.data.remote.addon.AddonRemoteDataSource
import com.drift.stream.data.remote.addon.AddonUrlBuilder
import com.drift.stream.data.remote.addon.ManifestDto
import com.drift.stream.di.DefaultDispatcher
import com.drift.stream.domain.model.Addon
import com.drift.stream.domain.model.AddonManifest
import com.drift.stream.domain.model.AddonOwner
import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.AppError
import com.drift.stream.domain.model.Outcome
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.AddonRepository
import com.drift.stream.domain.repository.ContentPrefsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Suppress("LongParameterList", "TooManyFunctions")
class AddonRepositoryImpl(
    private val addonDao: AddonDao,
    private val profileAddonPrefsDao: ProfileAddonPrefsDao,
    private val activeProfile: ActiveProfileProvider,
    private val remoteDataSource: AddonRemoteDataSource,
    private val transactionRunner: TransactionRunner,
    private val json: Json,
    private val contentPrefsRepository: ContentPrefsRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : AddonRepository {
    @Inject
    constructor(
        addonDao: AddonDao,
        profileAddonPrefsDao: ProfileAddonPrefsDao,
        activeProfile: ActiveProfileProvider,
        remoteDataSource: AddonRemoteDataSource,
        transactionRunner: TransactionRunner,
        json: Json,
        contentPrefsRepository: ContentPrefsRepository,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        addonDao = addonDao,
        profileAddonPrefsDao = profileAddonPrefsDao,
        activeProfile = activeProfile,
        remoteDataSource = remoteDataSource,
        transactionRunner = transactionRunner,
        json = json,
        contentPrefsRepository = contentPrefsRepository,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override fun observeAll(): Flow<List<Addon>> =
        activeProfile.scopedFlow { profileId ->
            combine(
                addonDao.observeAll(profileId),
                addonDao.observeSharedByOthers(profileId),
            ) { ownEntities, sharedRows ->
                withContext(dispatcher) {
                    val own = ownEntities.mapNotNull { it.toDomain(json) }
                    val shared = sharedRows.mapNotNull { it.toDomain(json) }
                    own + shared
                }
            }
        }

    override fun observeEnabled(): Flow<List<Addon>> =
        activeProfile.scopedFlow { profileId ->
            combine(
                addonDao.observeAll(profileId),
                addonDao.observeSharedByOthers(profileId),
            ) { ownEntities, sharedRows ->
                withContext(dispatcher) {
                    val own = ownEntities.mapNotNull { it.toDomain(json) }
                    val shared = sharedRows.mapNotNull { it.toDomain(json) }
                    val ownEnabled = own.filter { it.enabled }
                    val sharedEnabled =
                        shared
                            .filter { it.enabled }
                            .sortedWith(
                                compareBy(
                                    { (it.owner as? AddonOwner.Other)?.profileId ?: Long.MAX_VALUE },
                                    { it.position },
                                ),
                            )
                    ownEnabled + sharedEnabled
                }
            }
        }

    override fun observeActive(): Flow<List<Addon>> =
        combine(observeEnabled(), contentPrefsRepository.familyMode) { enabled, familyMode ->
            if (familyMode) enabled.filterNot { it.isAdult } else enabled
        }.distinctUntilChanged()

    override fun observeHiddenSharedCount(): Flow<Int> =
        activeProfile.scopedFlow { profileId ->
            addonDao.observeHiddenSharedCount(profileId)
        }

    override suspend fun install(transportUrl: String): Outcome<Addon> {
        val normalizedBaseUrl =
            AddonUrlBuilder.normalize(transportUrl)?.toString()
                ?: return Outcome.Failure(AppError.InvalidUrl)

        val profileId = activeProfile.requireId()

        val fetchOutcome = remoteDataSource.manifestWithRaw(normalizedBaseUrl)
        return when (fetchOutcome) {
            is Outcome.Failure -> {
                fetchOutcome
            }

            is Outcome.Success -> {
                val fetched = fetchOutcome.value
                val manifest = fetched.manifest
                if (manifest.resources.isEmpty()) {
                    Outcome.Failure(AppError.Parse)
                } else {
                    transactionRunner.run {
                        val existing = addonDao.get(profileId, normalizedBaseUrl)
                        val entity =
                            if (existing != null) {
                                existing.copy(
                                    id = manifest.id,
                                    name = manifest.name,
                                    version = manifest.version,
                                    manifestJson = fetched.rawJson,
                                )
                            } else {
                                val currentCount = addonDao.getAllForProfile(profileId).size
                                AddonEntity(
                                    transportUrl = normalizedBaseUrl,
                                    id = manifest.id,
                                    name = manifest.name,
                                    version = manifest.version,
                                    manifestJson = fetched.rawJson,
                                    enabled = true,
                                    position = currentCount,
                                    source = AddonSource.LOCAL,
                                    installedAt = clock(),
                                    profileId = profileId,
                                    shared = false,
                                )
                            }
                        addonDao.upsert(entity)
                        Outcome.Success(
                            Addon(
                                transportUrl = entity.transportUrl,
                                manifest = manifest,
                                enabled = entity.enabled,
                                position = entity.position,
                                source = entity.source,
                                installedAt = entity.installedAt,
                                shared = entity.shared,
                                owner = AddonOwner.Own,
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun refresh(transportUrl: String): Outcome<Addon> {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString()
        val profileId = activeProfile.requireId()
        val isInstalled = normalizedBaseUrl != null && addonDao.get(profileId, normalizedBaseUrl) != null
        if (normalizedBaseUrl == null || !isInstalled) {
            val error = if (normalizedBaseUrl == null) AppError.InvalidUrl else AppError.NotFound
            return Outcome.Failure(error)
        }

        return when (val fetchOutcome = remoteDataSource.manifestWithRaw(normalizedBaseUrl)) {
            is Outcome.Failure -> {
                fetchOutcome
            }

            is Outcome.Success -> {
                val fetched = fetchOutcome.value
                val manifest = fetched.manifest
                if (manifest.resources.isEmpty()) {
                    Outcome.Failure(AppError.Parse)
                } else {
                    transactionRunner.run {
                        val current = addonDao.get(profileId, normalizedBaseUrl)
                        if (current == null) {
                            Outcome.Failure(AppError.NotFound)
                        } else {
                            val updatedEntity =
                                current.copy(
                                    id = manifest.id,
                                    name = manifest.name,
                                    version = manifest.version,
                                    manifestJson = fetched.rawJson,
                                )
                            addonDao.upsert(updatedEntity)
                            Outcome.Success(
                                Addon(
                                    transportUrl = updatedEntity.transportUrl,
                                    manifest = manifest,
                                    enabled = updatedEntity.enabled,
                                    position = updatedEntity.position,
                                    source = updatedEntity.source,
                                    installedAt = updatedEntity.installedAt,
                                    shared = updatedEntity.shared,
                                    owner = AddonOwner.Own,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun fetchManifest(transportUrl: String): Outcome<AddonManifest> {
        val normalizedBaseUrl =
            AddonUrlBuilder.normalize(transportUrl)?.toString()
                ?: return Outcome.Failure(AppError.InvalidUrl)
        return remoteDataSource.manifest(normalizedBaseUrl)
    }

    override suspend fun remove(transportUrl: String) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        val profileId = activeProfile.requireId()
        transactionRunner.run {
            if (addonDao.get(profileId, normalizedBaseUrl) == null) return@run
            addonDao.delete(profileId, normalizedBaseUrl)
            val remaining = addonDao.getAllForProfile(profileId)
            val renumbered =
                remaining.mapIndexed { index, entity ->
                    entity.copy(position = index)
                }
            addonDao.updateAll(renumbered)
            profileAddonPrefsDao.deleteForAddon(profileId, normalizedBaseUrl)
        }
    }

    override suspend fun setEnabled(
        transportUrl: String,
        enabled: Boolean,
    ) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        val profileId = activeProfile.requireId()
        if (addonDao.get(profileId, normalizedBaseUrl) != null) {
            addonDao.setEnabled(profileId, normalizedBaseUrl, enabled)
        }
    }

    override suspend fun move(
        transportUrl: String,
        toPosition: Int,
    ) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        val profileId = activeProfile.requireId()
        transactionRunner.run {
            val all = addonDao.getAllForProfile(profileId)
            val urls = all.map { it.transportUrl }
            if (normalizedBaseUrl !in urls) return@run

            val newOrder = AddonReorder.reorder(urls, normalizedBaseUrl, toPosition)
            val byUrl = all.associateBy { it.transportUrl }
            val updated =
                newOrder.mapIndexed { index, url ->
                    byUrl.getValue(url).copy(position = index)
                }
            addonDao.updateAll(updated)
        }
    }

    override suspend fun setShared(
        transportUrl: String,
        shared: Boolean,
    ): Outcome<Unit> {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        val profileId = activeProfile.requireId()
        return transactionRunner.run {
            if (addonDao.get(profileId, normalizedBaseUrl) == null) {
                return@run Outcome.Failure(AppError.NotFound)
            }
            val rows = addonDao.setShared(profileId, normalizedBaseUrl, shared)
            if (rows <= 0) {
                return@run Outcome.Failure(AppError.NotFound)
            }
            if (!shared) {
                profileAddonPrefsDao.deleteForAddon(profileId, normalizedBaseUrl)
            }
            Outcome.Success(Unit)
        }
    }

    override suspend fun setSharedEnabled(
        ownerProfileId: Long,
        transportUrl: String,
        enabled: Boolean,
    ) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        val profileId = activeProfile.requireId()
        profileAddonPrefsDao.upsert(
            ProfileAddonPrefEntity(
                profileId = profileId,
                ownerProfileId = ownerProfileId,
                transportUrl = normalizedBaseUrl,
                enabled = enabled,
            ),
        )
    }
}

private fun AddonEntity.toDomain(
    json: Json,
    overrideEnabled: Boolean? = null,
    owner: AddonOwner = AddonOwner.Own,
): Addon? =
    try {
        val dto = json.decodeFromString<ManifestDto>(manifestJson)
        val manifest = AddonMappers.mapManifest(dto, json)
        Addon(
            transportUrl = transportUrl,
            manifest = manifest,
            enabled = overrideEnabled ?: enabled,
            position = position,
            source = source,
            installedAt = installedAt,
            shared = shared,
            owner = owner,
        )
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

private fun SharedAddonRow.toDomain(json: Json): Addon? =
    addon.toDomain(
        json = json,
        overrideEnabled = enabledForMe,
        owner = AddonOwner.Other(profileId = addon.profileId, name = ownerName),
    )
