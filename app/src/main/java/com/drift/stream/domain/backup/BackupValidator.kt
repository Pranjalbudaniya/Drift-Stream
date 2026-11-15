package com.drift.stream.domain.backup

import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.ListStatus
import com.drift.stream.domain.profile.isValidAvatarEmoji
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Base64

private const val MAX_NAME_CODE_POINTS = 20
private const val MAX_NAME_LENGTH = 500
private const val MAX_URL_LENGTH = 2000
private const val MAX_ID_LENGTH = 200
private const val MAX_PROGRESS_ID_LENGTH = 300
private const val MAX_ADDON_NAME_LENGTH = 200
private const val MAX_ADDON_VERSION_LENGTH = 50
private const val MAX_MANIFEST_JSON_BYTES = 1024 * 1024 // 1 MiB
private const val MAX_PHOTO_BYTES = 512 * 1024 // 512 KB
private const val MIN_JPEG_BYTES = 3
private const val COLOR_INDEX_MODULO = 12
private const val OPAQUE_ALPHA_MASK = 0xFFL shl 24
private const val RGB_HEX_MASK = 0xFFFFFFL
private const val JPEG_BYTE_0 = 0xFF
private const val JPEG_BYTE_1 = 0xD8
private const val JPEG_BYTE_2 = 0xFF

data class ProfileValidationResult(
    val validatedProfile: ValidatedProfile,
    val skipped: Int,
)

object BackupValidator {
    fun validateProfile(profile: BackupProfile): ProfileValidationResult {
        val cleanName = cleanProfileName(profile.name)
        val validAvatar = validateAvatar(profile.avatar)
        val (library, libSkipped) = processLibrary(profile.library)
        val (progress, progSkipped) = processProgress(profile.progress)
        val (search, searchSkipped) = processSearch(profile.searchHistory)
        val (addons, addonsSkipped) = processAddons(profile.addons)

        var settingsSkipped = 0
        val validSettings =
            profile.settings.associate { store ->
                val typedResult = BackupPrefs.toTypedValues(store.store, store.entries)
                settingsSkipped += typedResult.skipped
                store.store to typedResult.values
            }

        val totalSkipped = libSkipped + progSkipped + searchSkipped + addonsSkipped + settingsSkipped
        val validated =
            ValidatedProfile(
                name = cleanName,
                avatar = validAvatar,
                library = library,
                progress = progress,
                searchHistory = search,
                settings = validSettings,
                addons = addons,
            )
        return ProfileValidationResult(validated, totalSkipped)
    }

    fun cleanProfileName(raw: String): String {
        val withoutControl = StringBuilder()
        for (cp in raw.codePoints()) {
            if (!Character.isISOControl(cp)) {
                withoutControl.appendCodePoint(cp)
            }
        }
        val trimmed = withoutControl.toString().trim()
        val limited = StringBuilder()
        var count = 0
        for (cp in trimmed.codePoints()) {
            if (count >= MAX_NAME_CODE_POINTS) break
            limited.appendCodePoint(cp)
            count++
        }
        val result = limited.toString().trim()
        return if (result.isBlank()) "Imported profile" else result
    }
}

private fun processLibrary(library: List<BackupLibraryItem>): Pair<List<BackupLibraryItem>, Int> {
    var skipped = 0
    val validList = mutableListOf<BackupLibraryItem>()
    for (item in library) {
        val valid = validateLibraryItem(item)
        if (valid != null) {
            validList.add(valid)
        } else {
            skipped++
        }
    }
    val deduped = mutableListOf<BackupLibraryItem>()
    validList.groupBy { it.metaId }.forEach { (_, group) ->
        if (group.size > 1) {
            skipped += (group.size - 1)
            deduped.add(group.maxBy { it.addedAt })
        } else {
            deduped.add(group.first())
        }
    }
    return deduped to skipped
}

private fun processProgress(progress: List<BackupProgressItem>): Pair<List<BackupProgressItem>, Int> {
    var skipped = 0
    val validList = mutableListOf<BackupProgressItem>()
    for (item in progress) {
        val valid = validateProgressItem(item)
        if (valid != null) {
            validList.add(valid)
        } else {
            skipped++
        }
    }
    val deduped = mutableListOf<BackupProgressItem>()
    validList.groupBy { it.key }.forEach { (_, group) ->
        if (group.size > 1) {
            skipped += (group.size - 1)
            deduped.add(group.maxBy { it.updatedAt })
        } else {
            deduped.add(group.first())
        }
    }
    return deduped to skipped
}

private fun processSearch(searchHistory: List<BackupSearchItem>?): Pair<List<BackupSearchItem>?, Int> {
    if (searchHistory == null) return null to 0
    var skipped = 0
    val validList = mutableListOf<BackupSearchItem>()
    for (item in searchHistory) {
        if (item.query.isNotBlank() && item.query.length <= MAX_ID_LENGTH && item.lastUsedAt >= 0) {
            validList.add(item)
        } else {
            skipped++
        }
    }
    val deduped = mutableListOf<BackupSearchItem>()
    validList.groupBy { it.query }.forEach { (_, group) ->
        if (group.size > 1) {
            skipped += (group.size - 1)
            deduped.add(group.maxBy { it.lastUsedAt })
        } else {
            deduped.add(group.first())
        }
    }
    return deduped to skipped
}

private fun processAddons(addons: List<BackupAddonItem>?): Pair<List<BackupAddonItem>?, Int> {
    if (addons == null) return null to 0
    var skipped = 0
    val validList = mutableListOf<BackupAddonItem>()
    for (item in addons) {
        val valid = validateAddonItem(item)
        if (valid != null) {
            validList.add(valid)
        } else {
            skipped++
        }
    }
    val deduped = mutableListOf<BackupAddonItem>()
    validList.groupBy { it.transportUrl }.forEach { (_, group) ->
        if (group.size > 1) {
            skipped += (group.size - 1)
        }
        deduped.add(group.first())
    }
    return deduped to skipped
}

private fun validateAvatar(avatar: BackupAvatar): ValidatedAvatar {
    val normalizedColorIndex = Math.floorMod(avatar.colorIndex, COLOR_INDEX_MODULO)
    val opaqueCustomColor =
        avatar.customColor?.let {
            (OPAQUE_ALPHA_MASK or (it.toLong() and RGB_HEX_MASK)).toInt()
        }
    val cleanEmoji =
        if (avatar.emoji != null && isValidAvatarEmoji(avatar.emoji)) {
            avatar.emoji
        } else {
            null
        }

    val photoBytes =
        avatar.photoJpegBase64?.let { base64 ->
            val bytes = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
            val sizeOk = bytes != null && bytes.size in MIN_JPEG_BYTES..MAX_PHOTO_BYTES
            val b01Ok = bytes != null && bytes[0] == JPEG_BYTE_0.toByte() && bytes[1] == JPEG_BYTE_1.toByte()
            val b2Ok = bytes != null && bytes[2] == JPEG_BYTE_2.toByte()
            val headerOk = b01Ok && b2Ok
            if (sizeOk && headerOk) bytes else null
        }

    return ValidatedAvatar(
        colorIndex = normalizedColorIndex,
        customColor = opaqueCustomColor,
        emoji = cleanEmoji,
        photoJpegBytes = photoBytes,
    )
}

private fun isValidHttpUrl(url: String?): Boolean =
    url != null && url.length <= MAX_URL_LENGTH && (url.startsWith("http://") || url.startsWith("https://"))

private fun validateLibraryItem(item: BackupLibraryItem): BackupLibraryItem? {
    val idOk = item.metaId.isNotBlank() && item.metaId.length <= MAX_ID_LENGTH && item.addedAt >= 0
    val contentOk = item.type.isNotBlank() && item.name.isNotBlank()
    val statusOk = runCatching { ListStatus.valueOf(item.listStatus) }.isSuccess
    return if (idOk && contentOk && statusOk) {
        val validPoster = if (isValidHttpUrl(item.poster)) item.poster else null
        val validBackground = if (isValidHttpUrl(item.background)) item.background else null
        BackupLibraryItem(
            metaId = item.metaId,
            type = item.type,
            name = item.name.take(MAX_NAME_LENGTH),
            poster = validPoster,
            background = validBackground,
            inWatchlist = item.inWatchlist,
            addedAt = item.addedAt,
            listStatus = item.listStatus,
        )
    } else {
        null
    }
}

private fun validateProgressItem(item: BackupProgressItem): BackupProgressItem? {
    val idsOk =
        listOf(
            item.key,
            item.metaId,
            item.videoId,
        ).all { it.isNotBlank() && it.length <= MAX_PROGRESS_ID_LENGTH }
    val numbersOk =
        minOf(item.positionMs, item.durationMs, item.updatedAt) >= 0 && minOf(item.season ?: 0, item.episode ?: 0) >= 0
    val valid = idsOk && numbersOk && item.type.isNotBlank()
    return if (valid) {
        val clamped = if (item.durationMs in 1 until item.positionMs) item.durationMs else item.positionMs
        item.copy(positionMs = clamped)
    } else {
        null
    }
}

private fun validateAddonItem(item: BackupAddonItem): BackupAddonItem? {
    val urlOk =
        (item.transportUrl.startsWith("http://") || item.transportUrl.startsWith("https://")) &&
            item.transportUrl.length <= MAX_URL_LENGTH
    val metaOk = item.id.isNotBlank() && item.position >= 0
    val textOk = item.name.length <= MAX_ADDON_NAME_LENGTH && item.version.length <= MAX_ADDON_VERSION_LENGTH
    val sourceOk = runCatching { AddonSource.valueOf(item.source) }.isSuccess
    val manifestOk =
        item.manifestJson.length <= MAX_MANIFEST_JSON_BYTES &&
            runCatching { Json.parseToJsonElement(item.manifestJson) is JsonObject }.getOrDefault(false)

    val structureOk = urlOk && metaOk
    val payloadOk = textOk && sourceOk && manifestOk
    return if (structureOk && payloadOk) item else null
}
