package com.drift.stream.domain.backup

import kotlinx.serialization.json.Json

data class BackupLimits(
    val maxBytes: Long = BACKUP_MAX_BYTES.toLong(),
    val maxProfiles: Int = 100,
    val maxItemsPerList: Int = 200_000,
    val maxAddons: Int = 200,
    val maxSettingsPerStore: Int = 5_000,
)

sealed interface RejectReason {
    data object NotABackup : RejectReason

    data object NewerVersion : RejectReason

    data object Corrupt : RejectReason

    data object NoProfiles : RejectReason

    data object TooManyItems : RejectReason

    data object TooLarge : RejectReason
}

sealed interface ParseOutcome {
    data class Ready(
        val preview: BackupPreview,
        val backup: ValidatedBackup,
        val skipped: Int,
    ) : ParseOutcome

    data class Rejected(
        val reason: RejectReason,
    ) : ParseOutcome
}

data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val profiles: List<PreviewProfile>,
)

data class PreviewProfile(
    val index: Int,
    val name: String,
    val libraryCount: Int,
    val progressCount: Int,
    val searchCount: Int? = null,
    val addonCount: Int? = null,
    val hasPhoto: Boolean = false,
)

data class ValidatedBackup(
    val format: String,
    val backupVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val profiles: List<ValidatedProfile>,
    val skipped: Int = 0,
)

data class ValidatedProfile(
    val name: String,
    val avatar: ValidatedAvatar,
    val library: List<BackupLibraryItem>,
    val progress: List<BackupProgressItem>,
    val searchHistory: List<BackupSearchItem>?,
    val settings: Map<String, Map<String, Any>>,
    val addons: List<BackupAddonItem>?,
)

data class ValidatedAvatar(
    val colorIndex: Int,
    val customColor: Int? = null,
    val emoji: String? = null,
    val photoJpegBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValidatedAvatar) return false
        if (colorIndex != other.colorIndex) return false
        if (customColor != other.customColor) return false
        if (emoji != other.emoji) return false
        if (photoJpegBytes != null) {
            if (other.photoJpegBytes == null) return false
            if (!photoJpegBytes.contentEquals(other.photoJpegBytes)) return false
        } else if (other.photoJpegBytes != null) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = colorIndex
        result = 31 * result + (customColor ?: 0)
        result = 31 * result + (emoji?.hashCode() ?: 0)
        result = 31 * result + (photoJpegBytes?.contentHashCode() ?: 0)
        return result
    }
}

object BackupParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

    fun parse(
        bytes: ByteArray,
        limits: BackupLimits = BackupLimits(),
    ): ParseOutcome =
        when {
            bytes.size > limits.maxBytes -> ParseOutcome.Rejected(RejectReason.TooLarge)
            else -> parseContent(bytes, limits)
        }

    private fun parseContent(
        bytes: ByteArray,
        limits: BackupLimits,
    ): ParseOutcome {
        val file = decodeBackupFile(bytes) ?: return ParseOutcome.Rejected(RejectReason.Corrupt)
        val rejection = checkHeader(file) ?: checkLimits(file, limits)
        return rejection?.let { ParseOutcome.Rejected(it) } ?: buildReady(file)
    }

    private fun decodeBackupFile(bytes: ByteArray): BackupFile? =
        try {
            json.decodeFromString(BackupFile.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            null
        }

    private fun checkHeader(file: BackupFile): RejectReason? =
        when {
            file.format != BACKUP_FORMAT -> RejectReason.NotABackup
            file.backupVersion > BACKUP_VERSION -> RejectReason.NewerVersion
            file.backupVersion < 1 -> RejectReason.Corrupt
            file.profiles.isEmpty() -> RejectReason.NoProfiles
            else -> null
        }

    private fun checkLimits(
        file: BackupFile,
        limits: BackupLimits,
    ): RejectReason? =
        when {
            file.profiles.size > limits.maxProfiles -> RejectReason.TooManyItems
            file.profiles.any { isProfileOverLimit(it, limits) } -> RejectReason.TooManyItems
            else -> null
        }

    private fun isProfileOverLimit(
        p: BackupProfile,
        limits: BackupLimits,
    ): Boolean {
        val listOver = p.library.size > limits.maxItemsPerList || p.progress.size > limits.maxItemsPerList
        val searchOver = p.searchHistory?.let { it.size > limits.maxItemsPerList } ?: false
        val addonsOver = p.addons?.let { it.size > limits.maxAddons } ?: false
        val settingsOver = p.settings.any { it.entries.size > limits.maxSettingsPerStore }
        return listOver || searchOver || addonsOver || settingsOver
    }

    private fun buildReady(file: BackupFile): ParseOutcome.Ready {
        var totalSkipped = 0
        val validatedProfiles = mutableListOf<ValidatedProfile>()
        val previewProfiles = mutableListOf<PreviewProfile>()

        file.profiles.forEachIndexed { index, profile ->
            val result = BackupValidator.validateProfile(profile)
            totalSkipped += result.skipped
            validatedProfiles.add(result.validatedProfile)

            previewProfiles.add(
                PreviewProfile(
                    index = index,
                    name = result.validatedProfile.name,
                    libraryCount = result.validatedProfile.library.size,
                    progressCount = result.validatedProfile.progress.size,
                    searchCount = result.validatedProfile.searchHistory?.size,
                    addonCount = result.validatedProfile.addons?.size,
                    hasPhoto = result.validatedProfile.avatar.photoJpegBytes != null,
                ),
            )
        }

        val preview =
            BackupPreview(
                createdAt = file.createdAt,
                appVersion = file.appVersion,
                profiles = previewProfiles,
            )
        val backup =
            ValidatedBackup(
                format = file.format,
                backupVersion = file.backupVersion,
                appVersion = file.appVersion,
                createdAt = file.createdAt,
                profiles = validatedProfiles,
                skipped = totalSkipped,
            )

        return ParseOutcome.Ready(
            preview = preview,
            backup = backup,
            skipped = totalSkipped,
        )
    }
}
