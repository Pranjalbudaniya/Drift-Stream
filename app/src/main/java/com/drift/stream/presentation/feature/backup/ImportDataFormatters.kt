package com.drift.stream.presentation.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.drift.stream.R
import com.drift.stream.domain.backup.ImportSummary
import com.drift.stream.domain.backup.PreviewProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun formatProfileCounts(profile: PreviewProfile): String {
    val lib = pluralStringResource(R.plurals.import_summary_library, profile.libraryCount, profile.libraryCount)
    val prog = pluralStringResource(R.plurals.import_summary_history, profile.progressCount, profile.progressCount)
    val parts = mutableListOf(lib, prog)
    if (profile.searchCount != null && profile.searchCount > 0) {
        parts.add(pluralStringResource(R.plurals.import_summary_searches, profile.searchCount, profile.searchCount))
    }
    if (profile.addonCount != null && profile.addonCount > 0) {
        parts.add(pluralStringResource(R.plurals.import_summary_addons, profile.addonCount, profile.addonCount))
    }
    return parts.joinToString(" · ")
}

@Composable
fun formatSummaryText(summary: ImportSummary): String {
    val parts = mutableListOf<String>()
    if (summary.profilesCreated > 0) {
        parts.add(
            pluralStringResource(
                R.plurals.import_summary_profiles,
                summary.profilesCreated,
                summary.profilesCreated,
            ),
        )
    }
    parts.add(pluralStringResource(R.plurals.import_summary_library, summary.libraryItems, summary.libraryItems))
    parts.add(pluralStringResource(R.plurals.import_summary_history, summary.progressEntries, summary.progressEntries))
    parts.add(pluralStringResource(R.plurals.import_summary_searches, summary.searches, summary.searches))
    parts.add(pluralStringResource(R.plurals.import_summary_addons, summary.addons, summary.addons))
    val items = parts.joinToString(", ")
    val base = stringResource(R.string.import_summary_prefix, items)
    return if (summary.skipped > 0) {
        "$base. " + stringResource(R.string.import_summary_skipped, summary.skipped)
    } else {
        "$base."
    }
}

fun formatCreatedAt(epochMilli: Long): String {
    val date = Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
}
