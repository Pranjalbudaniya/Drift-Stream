package com.drift.stream.domain.backup

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MAX_SLUG_LENGTH = 20
private const val DEFAULT_SLUG = "profile"
private const val FILE_PREFIX = "drift-stream"
private const val FILE_EXTENSION = ".json"

fun backupFileName(
    scope: ExportScope,
    profileName: String? = null,
    date: LocalDate = LocalDate.now(),
): String {
    val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    return when (scope) {
        is ExportScope.All -> {
            "$FILE_PREFIX-backup-$dateStr$FILE_EXTENSION"
        }

        is ExportScope.Profile -> {
            val slug = slugifyProfileName(profileName.orEmpty())
            "$FILE_PREFIX-$slug-$dateStr$FILE_EXTENSION"
        }
    }
}

private fun slugifyProfileName(name: String): String {
    val lower = name.lowercase()
    val replaced =
        lower
            .map { ch ->
                if (ch in 'a'..'z' || ch in '0'..'9') ch else '-'
            }.joinToString("")
    val collapsed = replaced.replace(Regex("-+"), "-")
    val trimmed = collapsed.trim('-')
    val cut =
        if (trimmed.length > MAX_SLUG_LENGTH) {
            trimmed.take(MAX_SLUG_LENGTH).trimEnd('-')
        } else {
            trimmed
        }
    return cut.ifEmpty { DEFAULT_SLUG }
}
