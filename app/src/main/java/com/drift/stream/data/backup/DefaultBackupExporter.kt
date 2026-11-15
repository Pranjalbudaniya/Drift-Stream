package com.drift.stream.data.backup

import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.backup.BACKUP_FORMAT
import com.drift.stream.domain.backup.BACKUP_MAX_BYTES
import com.drift.stream.domain.backup.BACKUP_VERSION
import com.drift.stream.domain.backup.BackupAddonItem
import com.drift.stream.domain.backup.BackupAvatar
import com.drift.stream.domain.backup.BackupExporter
import com.drift.stream.domain.backup.BackupFile
import com.drift.stream.domain.backup.BackupLibraryItem
import com.drift.stream.domain.backup.BackupPrefs
import com.drift.stream.domain.backup.BackupProfile
import com.drift.stream.domain.backup.BackupProgressItem
import com.drift.stream.domain.backup.BackupSearchItem
import com.drift.stream.domain.backup.BackupSettingsStore
import com.drift.stream.domain.backup.ExportOptions
import com.drift.stream.domain.backup.ExportResult
import com.drift.stream.domain.backup.ExportScope
import com.drift.stream.domain.backup.ExportSummary
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.AppInfoProvider
import com.drift.stream.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_PHOTO_BYTES = 512 * 1024L

data class BackupDaos
    @Inject
    constructor(
        val library: LibraryDao,
        val progress: ProgressDao,
        val searchHistory: SearchHistoryDao,
        val addon: AddonDao,
    )

data class BackupEnvironment(
    val appInfoProvider: AppInfoProvider,
    val clock: Clock,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val maxBytes: Long = BACKUP_MAX_BYTES.toLong(),
) {
    @Inject
    constructor(
        appInfoProvider: AppInfoProvider,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        appInfoProvider = appInfoProvider,
        clock = clock,
        ioDispatcher = ioDispatcher,
        maxBytes = BACKUP_MAX_BYTES.toLong(),
    )
}

@Singleton
class DefaultBackupExporter
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val daos: BackupDaos,
        private val profileDataStores: ProfileDataStores,
        private val avatarPhotoStore: AvatarPhotoStore,
        private val env: BackupEnvironment,
    ) : BackupExporter {
        private val json =
            Json {
                encodeDefaults = true
                explicitNulls = false
            }

        override suspend fun export(
            scope: ExportScope,
            options: ExportOptions,
            sink: OutputStream,
        ): ExportResult =
            withContext(env.ioDispatcher) {
                try {
                    val profilesToExport =
                        when (scope) {
                            is ExportScope.Profile -> {
                                val profile =
                                    profileRepository.get(scope.profileId)
                                        ?: return@withContext ExportResult.NotFound
                                listOf(profile)
                            }

                            is ExportScope.All -> {
                                profileRepository.profiles.first()
                            }
                        }

                    val backupProfiles = profilesToExport.map { profile -> exportProfile(profile, options) }
                    val summary = buildSummary(backupProfiles)

                    val backupFile =
                        BackupFile(
                            format = BACKUP_FORMAT,
                            backupVersion = BACKUP_VERSION,
                            appVersion = env.appInfoProvider.versionName ?: "unknown",
                            createdAt = env.clock.millis(),
                            profiles = backupProfiles,
                        )

                    val jsonString = json.encodeToString(BackupFile.serializer(), backupFile)
                    val bytes = jsonString.toByteArray(Charsets.UTF_8)

                    if (bytes.size > env.maxBytes) {
                        return@withContext ExportResult.TooLarge
                    }

                    sink.write(bytes)
                    sink.flush()
                    ExportResult.Done(summary)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    ExportResult.Failed(e)
                } catch (e: SerializationException) {
                    ExportResult.Failed(e)
                } catch (e: IllegalStateException) {
                    ExportResult.Failed(e)
                } catch (e: IllegalArgumentException) {
                    ExportResult.Failed(e)
                } catch (e: NoSuchElementException) {
                    ExportResult.Failed(e)
                }
            }

        private fun buildSummary(backupProfiles: List<BackupProfile>): ExportSummary =
            ExportSummary(
                profiles = backupProfiles.size,
                libraryItems = backupProfiles.sumOf { it.library.size },
                progressEntries = backupProfiles.sumOf { it.progress.size },
                searches = backupProfiles.sumOf { it.searchHistory?.size ?: 0 },
                addons = backupProfiles.sumOf { it.addons?.size ?: 0 },
            )

        private suspend fun exportProfile(
            profile: Profile,
            options: ExportOptions,
        ): BackupProfile =
            BackupProfile(
                name = profile.name,
                avatar = exportAvatar(profile),
                library = exportLibrary(profile.id),
                progress = exportProgress(profile.id),
                searchHistory = exportSearch(profile.id, options.includeSearchHistory),
                settings = exportSettings(profile.id),
                addons = exportAddons(profile.id, options.includeAddons),
            )

        private suspend fun exportLibrary(profileId: Long): List<BackupLibraryItem> =
            daos.library.getAllForProfile(profileId).map { entity ->
                BackupLibraryItem(
                    metaId = entity.metaId,
                    type = entity.type,
                    name = entity.name,
                    poster = entity.poster,
                    background = entity.background,
                    inWatchlist = entity.inWatchlist,
                    addedAt = entity.addedAt,
                    listStatus = entity.listStatus,
                )
            }

        private suspend fun exportProgress(profileId: Long): List<BackupProgressItem> =
            daos.progress.getAllForProfile(profileId).map { entity ->
                BackupProgressItem(
                    key = entity.key,
                    metaId = entity.metaId,
                    videoId = entity.videoId,
                    type = entity.type,
                    season = entity.season,
                    episode = entity.episode,
                    positionMs = entity.positionMs,
                    durationMs = entity.durationMs,
                    watched = entity.watched,
                    updatedAt = entity.updatedAt,
                )
            }

        private suspend fun exportSearch(
            profileId: Long,
            enabled: Boolean,
        ): List<BackupSearchItem>? =
            if (enabled) {
                daos.searchHistory.getAll(profileId).map { entity ->
                    BackupSearchItem(query = entity.query, lastUsedAt = entity.lastUsedAt)
                }
            } else {
                null
            }

        private suspend fun exportSettings(profileId: Long): List<BackupSettingsStore> =
            PersonalStore.entries.map { store ->
                val prefs = profileDataStores.storeFor(store, profileId).data.first()
                BackupSettingsStore(
                    store = store.legacyFileName,
                    entries = BackupPrefs.exportEntries(store, prefs),
                )
            }

        private suspend fun exportAddons(
            profileId: Long,
            enabled: Boolean,
        ): List<BackupAddonItem>? =
            if (enabled) {
                daos.addon.getAllForProfile(profileId).map { entity ->
                    BackupAddonItem(
                        transportUrl = entity.transportUrl,
                        id = entity.id,
                        name = entity.name,
                        version = entity.version,
                        manifestJson = entity.manifestJson,
                        enabled = entity.enabled,
                        position = entity.position,
                        source = entity.source.name,
                        installedAt = entity.installedAt,
                    )
                }
            } else {
                null
            }

        private fun exportAvatar(profile: Profile): BackupAvatar {
            val photoBase64 =
                if (profile.avatarPhotoStamp != null) {
                    val file = avatarPhotoStore.fileOf(profile.id)
                    if (file.exists() && file.isFile && file.length() in 1..MAX_PHOTO_BYTES) {
                        try {
                            Base64.getEncoder().encodeToString(file.readBytes())
                        } catch (_: IOException) {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }

            return BackupAvatar(
                colorIndex = profile.avatarColor,
                customColor = profile.avatarCustomColor,
                emoji = profile.avatarEmoji,
                photoJpegBase64 = photoBase64,
            )
        }
    }
