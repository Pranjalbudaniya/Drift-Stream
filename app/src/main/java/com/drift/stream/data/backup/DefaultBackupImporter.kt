package com.drift.stream.data.backup

import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.AddonDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.di.IoDispatcher
import com.drift.stream.domain.backup.BackupAddonItem
import com.drift.stream.domain.backup.BackupImporter
import com.drift.stream.domain.backup.BackupLibraryItem
import com.drift.stream.domain.backup.BackupMerge
import com.drift.stream.domain.backup.BackupProgressItem
import com.drift.stream.domain.backup.BackupSearchItem
import com.drift.stream.domain.backup.ImportMode
import com.drift.stream.domain.backup.ImportRequest
import com.drift.stream.domain.backup.ImportResult
import com.drift.stream.domain.backup.ImportSummary
import com.drift.stream.domain.backup.ImportWarning
import com.drift.stream.domain.backup.ValidatedBackup
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.sql.SQLException
import javax.inject.Inject
import javax.inject.Singleton

internal data class DbOutcome(
    val profilesCreated: Int = 0,
    val libraryItems: Int = 0,
    val progressEntries: Int = 0,
    val searches: Int = 0,
    val addons: Int = 0,
    val createdPhotos: List<Pair<Long, ByteArray?>> = emptyList(),
    val settingsToAdd: List<Pair<Long, Map<String, Map<String, Any>>>> = emptyList(),
    val settingsToReplace: List<Pair<Long, Map<String, Map<String, Any>>>> = emptyList(),
)

internal class TargetNotFoundException : Exception()

@Singleton
class DefaultBackupImporter
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val daos: BackupDaos,
        private val transactionRunner: TransactionRunner,
        private val afterCommitWriter: BackupAfterCommitWriter,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BackupImporter {
        private val daoWriter = BackupDaoWriter(daos)

        override suspend fun import(
            backup: ValidatedBackup,
            request: ImportRequest,
        ): ImportResult =
            withContext(ioDispatcher) {
                val outcome: DbOutcome =
                    try {
                        transactionRunner.run {
                            when (request.mode) {
                                is ImportMode.AddAsNew -> executeAddAsNew(backup, request)
                                is ImportMode.MergeInto -> executeMergeInto(backup, request, request.mode.profileId)
                                is ImportMode.ReplaceIn -> executeReplaceIn(backup, request, request.mode.profileId)
                            }
                        }
                    } catch (_: TargetNotFoundException) {
                        return@withContext ImportResult.TargetNotFound
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SQLException) {
                        return@withContext ImportResult.Failed(e)
                    } catch (e: IllegalStateException) {
                        return@withContext ImportResult.Failed(e)
                    } catch (e: IllegalArgumentException) {
                        return@withContext ImportResult.Failed(e)
                    } catch (e: IOException) {
                        return@withContext ImportResult.Failed(e)
                    }

                val warnings = afterCommitWriter.performWrites(outcome)
                val skippedCount = if (request.skipped > 0) request.skipped else backup.skipped

                ImportResult.Done(
                    ImportSummary(
                        profilesCreated = outcome.profilesCreated,
                        libraryItems = outcome.libraryItems,
                        progressEntries = outcome.progressEntries,
                        searches = outcome.searches,
                        addons = outcome.addons,
                        skipped = skippedCount,
                        warnings = warnings,
                    ),
                )
            }

        private suspend fun executeAddAsNew(
            backup: ValidatedBackup,
            request: ImportRequest,
        ): DbOutcome {
            val existingProfiles = profileRepository.profiles.first()
            val existingNames = existingProfiles.map { it.name }.toMutableList()

            var libraryTotal = 0
            var progressTotal = 0
            var searchesTotal = 0
            var addonsTotal = 0
            val photos = mutableListOf<Pair<Long, ByteArray?>>()
            val settings = mutableListOf<Pair<Long, Map<String, Map<String, Any>>>>()

            for (idx in request.selectedProfileIndexes) {
                val p = backup.profiles.getOrNull(idx) ?: continue
                val uniqueName = generateUniqueName(p.name, existingNames)
                existingNames.add(uniqueName)

                val draft =
                    ProfileDraft(
                        name = uniqueName,
                        avatarColor = p.avatar.colorIndex,
                        avatarEmoji = p.avatar.emoji,
                        avatarCustomColor = p.avatar.customColor,
                        avatarPhotoStamp = null,
                    )
                val createResult = profileRepository.create(draft)
                val newProfile =
                    (createResult as? CreateResult.Created)?.profile
                        ?: error("Failed to create profile: $uniqueName")

                val newId = newProfile.id
                daoWriter.insertLibrary(newId, p.library)
                libraryTotal += p.library.size
                daoWriter.insertProgress(newId, p.progress)
                progressTotal += p.progress.size

                if (p.searchHistory != null) {
                    daoWriter.insertSearch(newId, p.searchHistory)
                    searchesTotal += p.searchHistory.size
                }
                if (request.includeAddons && p.addons != null) {
                    daoWriter.insertAddons(newId, p.addons)
                    addonsTotal += p.addons.size
                }
                photos.add(newId to p.avatar.photoJpegBytes)
                settings.add(newId to p.settings)
            }

            return DbOutcome(
                profilesCreated = request.selectedProfileIndexes.size,
                libraryItems = libraryTotal,
                progressEntries = progressTotal,
                searches = searchesTotal,
                addons = addonsTotal,
                createdPhotos = photos,
                settingsToAdd = settings,
            )
        }

        private suspend fun executeMergeInto(
            backup: ValidatedBackup,
            request: ImportRequest,
            targetId: Long,
        ): DbOutcome {
            profileRepository.get(targetId) ?: throw TargetNotFoundException()
            val p = backup.profiles[request.selectedProfileIndexes.first()]

            val localLib = daoWriter.getLocalLibrary(targetId)
            val libMerged = BackupMerge.mergeLibrary(localLib, p.library)
            daoWriter.insertLibrary(targetId, libMerged.itemsToUpsert)

            val localProg = daoWriter.getLocalProgress(targetId)
            val progMerged = BackupMerge.mergeProgress(localProg, p.progress)
            daoWriter.insertProgress(targetId, progMerged.itemsToUpsert)

            var searchCount = 0
            if (p.searchHistory != null) {
                val localSearch = daos.searchHistory.getAll(targetId).map { BackupSearchItem(it.query, it.lastUsedAt) }
                val searchMerged = BackupMerge.mergeSearchHistory(localSearch, p.searchHistory)
                daoWriter.insertSearch(targetId, searchMerged.itemsToUpsert)
                searchCount = searchMerged.counts.added + searchMerged.counts.updated
            }

            var addonsCount = 0
            if (request.includeAddons && p.addons != null) {
                val localAddons = daoWriter.getLocalAddons(targetId)
                val addonMerged = BackupMerge.mergeAddons(localAddons, p.addons)
                daoWriter.insertAddons(targetId, addonMerged.itemsToUpsert)
                addonsCount = addonMerged.counts.added + addonMerged.counts.updated
            }

            return DbOutcome(
                profilesCreated = 0,
                libraryItems = libMerged.counts.added + libMerged.counts.updated,
                progressEntries = progMerged.counts.added + progMerged.counts.updated,
                searches = searchCount,
                addons = addonsCount,
            )
        }

        private suspend fun executeReplaceIn(
            backup: ValidatedBackup,
            request: ImportRequest,
            targetId: Long,
        ): DbOutcome {
            profileRepository.get(targetId) ?: throw TargetNotFoundException()
            val p = backup.profiles[request.selectedProfileIndexes.first()]

            daos.library.deleteAllForProfile(targetId)
            daoWriter.insertLibrary(targetId, p.library)

            daos.progress.deleteAllForProfile(targetId)
            daoWriter.insertProgress(targetId, p.progress)

            var searchCount = 0
            if (p.searchHistory != null) {
                daos.searchHistory.deleteAllForProfile(targetId)
                daoWriter.insertSearch(targetId, p.searchHistory)
                searchCount = p.searchHistory.size
            }

            var addonsCount = 0
            if (request.includeAddons && p.addons != null) {
                daos.addon.deleteAllForProfile(targetId)
                daoWriter.insertAddons(targetId, p.addons)
                addonsCount = p.addons.size
            }

            return DbOutcome(
                profilesCreated = 0,
                libraryItems = p.library.size,
                progressEntries = p.progress.size,
                searches = searchCount,
                addons = addonsCount,
                settingsToReplace = listOf(targetId to p.settings),
            )
        }

        companion object {
            fun generateUniqueName(
                baseName: String,
                existingNames: List<String>,
            ): String {
                val trimmed = baseName.trim().ifEmpty { "Profile" }
                if (!existingNames.any { it.equals(trimmed, ignoreCase = true) }) {
                    return trimmed
                }
                var counter = 2
                while (true) {
                    val suffix = " ($counter)"
                    val maxBaseLen = 20 - suffix.length
                    val truncatedBase = truncateCodePoints(trimmed, maxBaseLen).trim()
                    val candidate = "$truncatedBase$suffix"
                    if (!existingNames.any { it.equals(candidate, ignoreCase = true) }) {
                        return candidate
                    }
                    counter++
                }
            }

            private fun truncateCodePoints(
                text: String,
                maxCodePoints: Int,
            ): String {
                var codePointCount = 0
                var offset = 0
                while (offset < text.length && codePointCount < maxCodePoints) {
                    val codePoint = text.codePointAt(offset)
                    offset += Character.charCount(codePoint)
                    codePointCount++
                }
                return text.substring(0, offset)
            }
        }
    }
