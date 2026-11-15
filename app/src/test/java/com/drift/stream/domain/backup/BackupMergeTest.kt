package com.drift.stream.domain.backup

import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {
    private fun sampleLibrary(
        metaId: String,
        addedAt: Long = 1000L,
        name: String = "Movie $metaId",
    ): BackupLibraryItem =
        BackupLibraryItem(
            metaId = metaId,
            type = "movie",
            name = name,
            inWatchlist = true,
            addedAt = addedAt,
            listStatus = ListStatus.PLAN_TO_WATCH.name,
        )

    private fun sampleProgress(
        key: String,
        updatedAt: Long = 1000L,
        positionMs: Long = 500L,
    ): BackupProgressItem =
        BackupProgressItem(
            key = key,
            metaId = "m_$key",
            videoId = "v_$key",
            type = "movie",
            positionMs = positionMs,
            durationMs = 1000L,
            watched = false,
            updatedAt = updatedAt,
        )

    private fun sampleSearch(
        query: String,
        lastUsedAt: Long = 1000L,
    ): BackupSearchItem =
        BackupSearchItem(
            query = query,
            lastUsedAt = lastUsedAt,
        )

    private fun sampleAddon(
        url: String,
        position: Int = 0,
    ): BackupAddonItem =
        BackupAddonItem(
            transportUrl = url,
            id = "id_$url",
            name = "Name $url",
            version = "1.0",
            manifestJson = "{}",
            enabled = true,
            position = position,
            source = AddonSource.LOCAL.name,
            installedAt = 1000L,
        )

    @Test
    fun MR1_library_unionByMetaId() {
        val local = listOf(sampleLibrary("m1"), sampleLibrary("m2"))
        val imported = listOf(sampleLibrary("m2"), sampleLibrary("m3"))

        val result = BackupMerge.mergeLibrary(local, imported)
        assertEquals(2, result.counts.added + result.counts.updated + result.counts.unchanged)
        assertTrue(result.itemsToUpsert.any { it.metaId == "m3" })
    }

    @Test
    fun MR2_library_sameMetaId_laterAddedAtWins_equalKeepsLocal() {
        val local =
            listOf(
                sampleLibrary("m1", addedAt = 100L, name = "Local M1"),
                sampleLibrary("m2", addedAt = 200L, name = "Local M2"),
            )
        val imported =
            listOf(
                sampleLibrary("m1", addedAt = 200L, name = "Imported M1"),
                sampleLibrary("m2", addedAt = 200L, name = "Imported M2"),
            )

        val result = BackupMerge.mergeLibrary(local, imported)
        assertEquals(1, result.counts.updated)
        assertEquals(1, result.counts.unchanged)
        assertEquals(0, result.counts.added)

        assertEquals(1, result.itemsToUpsert.size)
        assertEquals("m1", result.itemsToUpsert[0].metaId)
        assertEquals("Imported M1", result.itemsToUpsert[0].name)
    }

    @Test
    fun MR3_progress_sameKey_newerUpdatedAtWins_equalKeepsLocal() {
        val local =
            listOf(
                sampleProgress("k1", updatedAt = 100L, positionMs = 100L),
                sampleProgress("k2", updatedAt = 200L, positionMs = 200L),
            )
        val imported =
            listOf(
                sampleProgress("k1", updatedAt = 200L, positionMs = 900L),
                sampleProgress("k2", updatedAt = 200L, positionMs = 900L),
            )

        val result = BackupMerge.mergeProgress(local, imported)
        assertEquals(1, result.counts.updated)
        assertEquals(1, result.counts.unchanged)
        assertEquals(0, result.counts.added)

        assertEquals(1, result.itemsToUpsert.size)
        assertEquals("k1", result.itemsToUpsert[0].key)
        assertEquals(900L, result.itemsToUpsert[0].positionMs)
    }

    @Test
    fun MR4_search_sameQuery_newerLastUsedAtWins() {
        val local =
            listOf(
                sampleSearch("dune", lastUsedAt = 100L),
                sampleSearch("alien", lastUsedAt = 200L),
            )
        val imported =
            listOf(
                sampleSearch("dune", lastUsedAt = 200L),
                sampleSearch("alien", lastUsedAt = 100L),
                sampleSearch("matrix", lastUsedAt = 300L),
            )

        val result = BackupMerge.mergeSearchHistory(local, imported)
        assertEquals(1, result.counts.added)
        assertEquals(1, result.counts.updated)
        assertEquals(1, result.counts.unchanged)

        assertEquals(2, result.itemsToUpsert.size)
        assertTrue(result.itemsToUpsert.any { it.query == "matrix" })
        assertTrue(result.itemsToUpsert.any { it.query == "dune" && it.lastUsedAt == 200L })
    }

    @Test
    fun MR5_addons_sameUrl_keepsLocal_newOnesAppendedAfterLocalPositions() {
        val local =
            listOf(
                sampleAddon("https://a/manifest.json", position = 0),
                sampleAddon("https://b/manifest.json", position = 1),
                sampleAddon("https://c/manifest.json", position = 2),
            )
        val imported =
            listOf(
                sampleAddon("https://b/manifest.json", position = 0),
                sampleAddon("https://d/manifest.json", position = 0),
                sampleAddon("https://e/manifest.json", position = 0),
            )

        val result = BackupMerge.mergeAddons(local, imported)
        assertEquals(2, result.counts.added)
        assertEquals(1, result.counts.unchanged)
        assertEquals(0, result.counts.updated)

        assertEquals(2, result.itemsToUpsert.size)
        val d = result.itemsToUpsert.first { it.transportUrl == "https://d/manifest.json" }
        val e = result.itemsToUpsert.first { it.transportUrl == "https://e/manifest.json" }
        assertEquals(3, d.position)
        assertEquals(4, e.position)
    }

    @Test
    fun MR6_counts_addedUpdatedUnchanged() {
        val local =
            listOf(
                sampleLibrary("m1", addedAt = 100L),
                sampleLibrary("m2", addedAt = 200L),
            )
        val imported =
            listOf(
                sampleLibrary("m1", addedAt = 300L), // updated
                sampleLibrary("m2", addedAt = 100L), // unchanged
                sampleLibrary("m3", addedAt = 150L), // added
            )

        val result = BackupMerge.mergeLibrary(local, imported)
        assertEquals(1, result.counts.added)
        assertEquals(1, result.counts.updated)
        assertEquals(1, result.counts.unchanged)
        assertEquals(2, result.itemsToUpsert.size)
    }

    @Test
    fun MR7_emptyImport_changesNothing() {
        val local = listOf(sampleLibrary("m1"))
        val imported = emptyList<BackupLibraryItem>()

        val result = BackupMerge.mergeLibrary(local, imported)
        assertEquals(0, result.counts.added)
        assertEquals(0, result.counts.updated)
        assertEquals(0, result.counts.unchanged)
        assertTrue(result.itemsToUpsert.isEmpty())
    }
}
