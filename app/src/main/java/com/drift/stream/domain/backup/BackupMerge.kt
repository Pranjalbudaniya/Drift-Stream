package com.drift.stream.domain.backup

data class MergeCounts(
    val added: Int,
    val updated: Int,
    val unchanged: Int,
)

data class MergeResult<T>(
    val itemsToUpsert: List<T>,
    val counts: MergeCounts,
)

object BackupMerge {
    fun mergeLibrary(
        local: List<BackupLibraryItem>,
        imported: List<BackupLibraryItem>,
    ): MergeResult<BackupLibraryItem> {
        val localMap = local.associateBy { it.metaId }
        val toUpsert = mutableListOf<BackupLibraryItem>()
        var added = 0
        var updated = 0
        var unchanged = 0

        for (item in imported) {
            val existing = localMap[item.metaId]
            if (existing == null) {
                toUpsert.add(item)
                added++
            } else if (item.addedAt > existing.addedAt) {
                toUpsert.add(item)
                updated++
            } else {
                unchanged++
            }
        }
        return MergeResult(toUpsert, MergeCounts(added, updated, unchanged))
    }

    fun mergeProgress(
        local: List<BackupProgressItem>,
        imported: List<BackupProgressItem>,
    ): MergeResult<BackupProgressItem> {
        val localMap = local.associateBy { it.key }
        val toUpsert = mutableListOf<BackupProgressItem>()
        var added = 0
        var updated = 0
        var unchanged = 0

        for (item in imported) {
            val existing = localMap[item.key]
            if (existing == null) {
                toUpsert.add(item)
                added++
            } else if (item.updatedAt > existing.updatedAt) {
                toUpsert.add(item)
                updated++
            } else {
                unchanged++
            }
        }
        return MergeResult(toUpsert, MergeCounts(added, updated, unchanged))
    }

    fun mergeSearchHistory(
        local: List<BackupSearchItem>,
        imported: List<BackupSearchItem>,
    ): MergeResult<BackupSearchItem> {
        val localMap = local.associateBy { it.query }
        val toUpsert = mutableListOf<BackupSearchItem>()
        var added = 0
        var updated = 0
        var unchanged = 0

        for (item in imported) {
            val existing = localMap[item.query]
            if (existing == null) {
                toUpsert.add(item)
                added++
            } else if (item.lastUsedAt > existing.lastUsedAt) {
                toUpsert.add(item)
                updated++
            } else {
                unchanged++
            }
        }
        return MergeResult(toUpsert, MergeCounts(added, updated, unchanged))
    }

    fun mergeAddons(
        local: List<BackupAddonItem>,
        imported: List<BackupAddonItem>,
    ): MergeResult<BackupAddonItem> {
        val localMap = local.associateBy { it.transportUrl }
        val toUpsert = mutableListOf<BackupAddonItem>()
        var added = 0
        var unchanged = 0
        var nextPosition = (local.maxOfOrNull { it.position } ?: -1) + 1

        for (item in imported) {
            val existing = localMap[item.transportUrl]
            if (existing == null) {
                toUpsert.add(item.copy(position = nextPosition++))
                added++
            } else {
                unchanged++
            }
        }
        return MergeResult(toUpsert, MergeCounts(added, updated = 0, unchanged))
    }
}
