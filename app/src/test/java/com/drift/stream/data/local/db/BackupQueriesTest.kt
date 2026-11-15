package com.drift.stream.data.local.db

import com.drift.stream.data.local.db.dao.FakeAddonDao
import com.drift.stream.data.local.db.dao.FakeLibraryDao
import com.drift.stream.data.local.db.dao.FakeProgressDao
import com.drift.stream.data.local.db.dao.FakeSearchHistoryDao
import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.domain.model.AddonSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupQueriesTest {
    @Test
    fun BQ1_libraryGetAll_returnsOnlyThatProfile_includingNotInWatchlist() =
        runTest {
            val libraryDao = FakeLibraryDao()

            val itemProfile1InWatchlist =
                LibraryItemEntity(
                    metaId = "meta_1",
                    type = "movie",
                    name = "Movie 1",
                    poster = null,
                    background = null,
                    inWatchlist = true,
                    addedAt = 1000L,
                    profileId = 1L,
                )
            val itemProfile1NotInWatchlist =
                LibraryItemEntity(
                    metaId = "meta_2",
                    type = "movie",
                    name = "Movie 2",
                    poster = null,
                    background = null,
                    inWatchlist = false,
                    addedAt = 2000L,
                    profileId = 1L,
                )
            val itemProfile2 =
                LibraryItemEntity(
                    metaId = "meta_3",
                    type = "series",
                    name = "Series 3",
                    poster = null,
                    background = null,
                    inWatchlist = true,
                    addedAt = 3000L,
                    profileId = 2L,
                )

            libraryDao.upsert(itemProfile1InWatchlist)
            libraryDao.upsert(itemProfile1NotInWatchlist)
            libraryDao.upsert(itemProfile2)

            val p1Items = libraryDao.getAllForProfile(1L)
            assertEquals(2, p1Items.size)
            assertTrue(p1Items.any { it.metaId == "meta_1" && it.inWatchlist })
            assertTrue(p1Items.any { it.metaId == "meta_2" && !it.inWatchlist })
            assertFalse(p1Items.any { it.profileId == 2L })
        }

    @Test
    fun BQ2_progressGetAll_returnsOnlyThatProfile() =
        runTest {
            val progressDao = FakeProgressDao()

            val p1Progress1 =
                WatchProgressEntity(
                    key = "k1",
                    metaId = "m1",
                    videoId = "v1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 1000L,
                    durationMs = 2000L,
                    watched = false,
                    updatedAt = 100L,
                    profileId = 1L,
                )
            val p1Progress2 =
                WatchProgressEntity(
                    key = "k2",
                    metaId = "m2",
                    videoId = "v2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 500L,
                    durationMs = 1000L,
                    watched = true,
                    updatedAt = 200L,
                    profileId = 1L,
                )
            val p2Progress =
                WatchProgressEntity(
                    key = "k3",
                    metaId = "m3",
                    videoId = "v3",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 800L,
                    durationMs = 1200L,
                    watched = false,
                    updatedAt = 300L,
                    profileId = 2L,
                )

            progressDao.upsert(p1Progress1)
            progressDao.upsert(p1Progress2)
            progressDao.upsert(p2Progress)

            val p1Results = progressDao.getAllForProfile(1L)
            assertEquals(2, p1Results.size)
            assertTrue(p1Results.any { it.key == "k1" })
            assertTrue(p1Results.any { it.key == "k2" })
            assertFalse(p1Results.any { it.profileId == 2L })
        }

    @Test
    fun BQ3_searchHistoryGetAll_returnsOnlyThatProfile() =
        runTest {
            val searchHistoryDao = FakeSearchHistoryDao()

            val p1Search1 = SearchHistoryEntity(query = "dune", lastUsedAt = 100L, profileId = 1L)
            val p1Search2 = SearchHistoryEntity(query = "alien", lastUsedAt = 200L, profileId = 1L)
            val p2Search = SearchHistoryEntity(query = "matrix", lastUsedAt = 300L, profileId = 2L)

            searchHistoryDao.upsert(p1Search1)
            searchHistoryDao.upsert(p1Search2)
            searchHistoryDao.upsert(p2Search)

            val p1Results = searchHistoryDao.getAll(1L)
            assertEquals(2, p1Results.size)
            assertEquals(listOf("alien", "dune"), p1Results.map { it.query })
            assertFalse(p1Results.any { it.profileId == 2L })
        }

    @Test
    fun BQ4_addonsGetAll_returnsOnlyThatProfilesOwnRows() =
        runTest {
            val addonDao = FakeAddonDao()

            val p1Addon =
                AddonEntity(
                    transportUrl = "https://example.com/p1/manifest.json",
                    id = "p1.addon",
                    name = "P1 Addon",
                    version = "1.0.0",
                    manifestJson = "{}",
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 1000L,
                    profileId = 1L,
                    shared = false,
                )
            val p2SharedAddon =
                AddonEntity(
                    transportUrl = "https://example.com/p2/manifest.json",
                    id = "p2.addon",
                    name = "P2 Shared Addon",
                    version = "1.0.0",
                    manifestJson = "{}",
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 2000L,
                    profileId = 2L,
                    shared = true,
                )

            addonDao.upsert(p1Addon)
            addonDao.upsert(p2SharedAddon)

            val p1Results = addonDao.getAllForProfile(1L)
            assertEquals(1, p1Results.size)
            assertEquals("p1.addon", p1Results[0].id)
            assertFalse(p1Results.any { it.profileId == 2L })
        }
}
