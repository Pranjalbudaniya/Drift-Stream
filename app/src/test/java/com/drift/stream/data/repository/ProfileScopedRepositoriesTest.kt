package com.drift.stream.data.repository

import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.FakeLibraryDao
import com.drift.stream.data.local.db.dao.FakeProgressDao
import com.drift.stream.data.local.db.dao.SearchHistoryDao
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.domain.model.ListStatus
import com.drift.stream.domain.model.MetaPreview
import com.drift.stream.domain.model.PosterShape
import com.drift.stream.domain.model.WatchProgress
import com.drift.stream.domain.usecase.ContinueWatchingRules
import com.drift.stream.testutil.FakeActiveProfileProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScopedRepositoriesTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val passThroughRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private fun testPreview(
        id: String,
        name: String = "Movie",
        type: String = "movie",
        poster: String? = null,
    ) = MetaPreview(
        id = id,
        type = type,
        name = name,
        poster = poster,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList(),
    )

    private class TestSearchHistoryDao : SearchHistoryDao {
        private val entries = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())

        override fun observeAll(profileId: Long): Flow<List<SearchHistoryEntity>> =
            entries.map { list -> list.filter { it.profileId == profileId } }

        override suspend fun getAll(profileId: Long): List<SearchHistoryEntity> =
            entries.value.filter { it.profileId == profileId }

        override suspend fun upsertAll(entities: List<SearchHistoryEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun upsert(entity: SearchHistoryEntity) {
            entries.update { list ->
                (list.filterNot { it.profileId == entity.profileId && it.query == entity.query } + entity)
                    .sortedByDescending { it.lastUsedAt }
            }
        }

        override suspend fun delete(
            profileId: Long,
            query: String,
        ) {
            entries.update { list ->
                list.filterNot { it.profileId == profileId && it.query == query }
            }
        }

        override suspend fun deleteAll(profileId: Long) {
            entries.update { list -> list.filterNot { it.profileId == profileId } }
        }

        override suspend fun deleteAllForProfile(profileId: Long) {
            deleteAll(profileId)
        }
    }

    @Test
    fun SW1_library_watchlist_isPerProfile() =
        runTest(testDispatcher) {
            val fakeLibraryDao = FakeLibraryDao()
            val activeProfile = FakeActiveProfileProvider(1L)
            val libraryRepo =
                LibraryRepositoryImpl(
                    libraryDao = fakeLibraryDao,
                    activeProfile = activeProfile,
                    transactionRunner = passThroughRunner,
                    dispatcher = testDispatcher,
                )
            val watchlistRepo = WatchlistRepositoryImpl(fakeLibraryDao, activeProfile)

            // Profile 1 adds m1
            libraryRepo.setStatus(
                testPreview(id = "m1", name = "Movie 1"),
                ListStatus.COMPLETED,
            )
            val p1Watchlist = watchlistRepo.observeWatchlist().first()
            assertEquals(1, p1Watchlist.size)
            assertEquals("m1", p1Watchlist[0].metaId)
            assertEquals(ListStatus.COMPLETED, p1Watchlist[0].status)

            // Switch to profile 2
            activeProfile.setActive(2L)
            val p2Initial = watchlistRepo.observeWatchlist().first()
            assertTrue(p2Initial.isEmpty())

            libraryRepo.setStatus(
                testPreview(id = "m2", name = "Movie 2"),
                ListStatus.PLAN_TO_WATCH,
            )
            val p2Watchlist = watchlistRepo.observeWatchlist().first()
            assertEquals(1, p2Watchlist.size)
            assertEquals("m2", p2Watchlist[0].metaId)
            assertEquals(ListStatus.PLAN_TO_WATCH, p2Watchlist[0].status)
        }

    @Test
    fun SW2_library_write_goesToTheActiveProfileOnly() =
        runTest(testDispatcher) {
            val fakeLibraryDao = FakeLibraryDao()
            val activeProfile = FakeActiveProfileProvider(1L)
            val libraryRepo =
                LibraryRepositoryImpl(
                    libraryDao = fakeLibraryDao,
                    activeProfile = activeProfile,
                    transactionRunner = passThroughRunner,
                    dispatcher = testDispatcher,
                )
            val watchlistRepo = WatchlistRepositoryImpl(fakeLibraryDao, activeProfile)

            libraryRepo.setStatus(
                testPreview(id = "m1", name = "Movie 1"),
                ListStatus.COMPLETED,
            )

            // Switch to profile 2 and add m3
            activeProfile.setActive(2L)
            libraryRepo.setStatus(
                testPreview(id = "m3", name = "Movie 3"),
                ListStatus.DROPPED,
            )

            // Switch back to profile 1
            activeProfile.setActive(1L)
            val p1Watchlist = watchlistRepo.observeWatchlist().first()
            assertEquals(listOf("m1"), p1Watchlist.map { it.metaId })
            assertEquals(ListStatus.COMPLETED, libraryRepo.getItem("m1")?.status)
            assertNull(libraryRepo.getItem("m3"))
        }

    @Test
    fun SW3_progress_continueRows_arePerProfile() =
        runTest(testDispatcher) {
            val fakeLibraryDao = FakeLibraryDao()
            val fakeProgressDao = FakeProgressDao(fakeLibraryDao)
            val activeProfile = FakeActiveProfileProvider(1L)
            val progressRepo =
                ProgressRepositoryImpl(
                    progressDao = fakeProgressDao,
                    libraryDao = fakeLibraryDao,
                    activeProfile = activeProfile,
                    transactionRunner = passThroughRunner,
                    dispatcher = testDispatcher,
                    clock = { 1000L },
                )

            insertMovieProgress(
                libraryDao = fakeLibraryDao,
                progressDao = fakeProgressDao,
                data =
                    MovieProgressData(
                        profileId = 1L,
                        name = "P1 Movie",
                        listStatus = "COMPLETED",
                        positionMs = 70_000L,
                        updatedAt = 1000L,
                    ),
            )
            insertMovieProgress(
                libraryDao = fakeLibraryDao,
                progressDao = fakeProgressDao,
                data =
                    MovieProgressData(
                        profileId = 2L,
                        name = "P2 Movie",
                        listStatus = "DROPPED",
                        positionMs = 80_000L,
                        updatedAt = 2000L,
                    ),
            )

            // Profile 1 sees only Profile 1 ContinueRows with Profile 1's listStatus
            val p1Rows = fakeProgressDao.observeContinueRows(1L).first()
            assertEquals(1, p1Rows.size)
            assertEquals(70_000L, p1Rows[0].positionMs)
            assertEquals("P1 Movie", p1Rows[0].name)
            assertEquals(ListStatus.COMPLETED, p1Rows[0].listStatus)

            // Profile 2 sees only Profile 2 ContinueRows with Profile 2's listStatus
            val p2Rows = fakeProgressDao.observeContinueRows(2L).first()
            assertEquals(1, p2Rows.size)
            assertEquals(80_000L, p2Rows[0].positionMs)
            assertEquals("P2 Movie", p2Rows[0].name)
            assertEquals(ListStatus.DROPPED, p2Rows[0].listStatus)

            // Repository observeContinueWatching also scopes correctly
            val p1Cw = progressRepo.observeContinueWatching().first()
            assertEquals(1, p1Cw.size)
            assertEquals("P1 Movie", p1Cw[0].name)

            activeProfile.setActive(2L)
            val p2Cw = progressRepo.observeContinueWatching().first()
            assertEquals(1, p2Cw.size)
            assertEquals("P2 Movie", p2Cw[0].name)
        }

    @Test
    fun SW4_progress_write_goesToTheActiveProfileOnly() =
        runTest(testDispatcher) {
            val fakeLibraryDao = FakeLibraryDao()
            val fakeProgressDao = FakeProgressDao(fakeLibraryDao)
            val activeProfile = FakeActiveProfileProvider(2L)
            val progressRepo =
                ProgressRepositoryImpl(
                    progressDao = fakeProgressDao,
                    libraryDao = fakeLibraryDao,
                    activeProfile = activeProfile,
                    transactionRunner = passThroughRunner,
                    dispatcher = testDispatcher,
                    clock = { 1000L },
                )

            progressRepo.upsert(
                WatchProgress(
                    key = "p2:movie|v1",
                    metaId = "p2:movie",
                    videoId = "v1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 50_000L,
                    durationMs = 100_000L,
                    watched = false,
                    updatedAt = 1000L,
                ),
            )
            assertEquals(50_000L, progressRepo.get("p2:movie|v1")?.positionMs)

            // Profile 1 must not see it
            activeProfile.setActive(1L)
            assertNull(progressRepo.get("p2:movie|v1"))
        }

    @Test
    fun SW5_history_clear_clearsOnlyTheActiveProfile() =
        runTest(testDispatcher) {
            val fakeLibraryDao = FakeLibraryDao()
            val fakeProgressDao = FakeProgressDao(fakeLibraryDao)
            val activeProfile = FakeActiveProfileProvider(1L)
            val historyRepo = HistoryRepositoryImpl(fakeProgressDao, activeProfile)

            fakeProgressDao.upsert(
                WatchProgressEntity(
                    key = "p1:h|v1",
                    metaId = "p1:h",
                    videoId = "v1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 1000L,
                    durationMs = 2000L,
                    watched = true,
                    updatedAt = 1000L,
                    profileId = 1L,
                ),
            )
            fakeProgressDao.upsert(
                WatchProgressEntity(
                    key = "p2:h|v1",
                    metaId = "p2:h",
                    videoId = "v1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 1000L,
                    durationMs = 2000L,
                    watched = true,
                    updatedAt = 1000L,
                    profileId = 2L,
                ),
            )

            activeProfile.setActive(2L)
            assertEquals(1, historyRepo.observeHistory(10).first().size)
            historyRepo.clear()
            assertEquals(0, historyRepo.observeHistory(10).first().size)

            activeProfile.setActive(1L)
            val p1History = historyRepo.observeHistory(10).first()
            assertEquals(1, p1History.size)
            assertEquals("p1:h", p1History[0].metaId)
        }

    @Test
    fun SW6_searchHistory_isPerProfile_andClearOnlyClearsTheActiveOne() =
        runTest(testDispatcher) {
            val dao = TestSearchHistoryDao()
            val activeProfile = FakeActiveProfileProvider(1L)
            val searchRepo =
                SearchHistoryRepositoryImpl(
                    dao = dao,
                    activeProfile = activeProfile,
                    dispatcher = testDispatcher,
                    clock = { 1000L },
                )

            searchRepo.save("profile 1 search")
            assertEquals(listOf("profile 1 search"), searchRepo.observeHistory().first())

            activeProfile.setActive(2L)
            assertTrue(searchRepo.observeHistory().first().isEmpty())

            searchRepo.save("profile 2 search")
            assertEquals(listOf("profile 2 search"), searchRepo.observeHistory().first())
            searchRepo.clear()
            assertTrue(searchRepo.observeHistory().first().isEmpty())

            activeProfile.setActive(1L)
            assertEquals(listOf("profile 1 search"), searchRepo.observeHistory().first())
        }

    @Test
    fun SW7_switchingBackRestoresTheFirstProfilesData() =
        runTest(testDispatcher) {
            val libraryDao = FakeLibraryDao()
            val progressDao = FakeProgressDao(libraryDao)
            val searchDao = TestSearchHistoryDao()
            val activeProfile = FakeActiveProfileProvider(1L)

            val libraryRepo =
                LibraryRepositoryImpl(
                    libraryDao = libraryDao,
                    activeProfile = activeProfile,
                    transactionRunner = passThroughRunner,
                    dispatcher = testDispatcher,
                )
            val watchlistRepo = WatchlistRepositoryImpl(libraryDao, activeProfile)
            val historyRepo = HistoryRepositoryImpl(progressDao, activeProfile)
            val searchRepo =
                SearchHistoryRepositoryImpl(
                    dao = searchDao,
                    activeProfile = activeProfile,
                    dispatcher = testDispatcher,
                    clock = { 1000L },
                )

            // Profile 1 data
            insertProfileSample(
                libraryRepo = libraryRepo,
                progressDao = progressDao,
                searchRepo = searchRepo,
                data = ProfileSampleData(1L, "p1", ListStatus.PLAN_TO_WATCH),
            )

            // Switch to Profile 2
            activeProfile.setActive(2L)
            insertProfileSample(
                libraryRepo = libraryRepo,
                progressDao = progressDao,
                searchRepo = searchRepo,
                data = ProfileSampleData(2L, "p2", ListStatus.COMPLETED),
            )

            assertEquals(listOf("p2_watch"), watchlistRepo.observeWatchlist().first().map { it.metaId })
            assertEquals(listOf("p2_hist"), historyRepo.observeHistory(10).first().map { it.metaId })
            assertEquals(listOf("p2_srch"), searchRepo.observeHistory().first())

            // Switch back to Profile 1
            activeProfile.setActive(1L)
            assertEquals(listOf("p1_watch"), watchlistRepo.observeWatchlist().first().map { it.metaId })
            assertEquals(listOf("p1_hist"), historyRepo.observeHistory(10).first().map { it.metaId })
            assertEquals(listOf("p1_srch"), searchRepo.observeHistory().first())
        }

    private data class MovieProgressData(
        val profileId: Long,
        val name: String,
        val listStatus: String,
        val positionMs: Long,
        val updatedAt: Long,
    )

    private data class ProfileSampleData(
        val profileId: Long,
        val prefix: String,
        val listStatus: ListStatus,
    )

    private suspend fun insertMovieProgress(
        libraryDao: FakeLibraryDao,
        progressDao: FakeProgressDao,
        data: MovieProgressData,
    ) {
        libraryDao.upsert(
            LibraryItemEntity(
                metaId = "shared:movie",
                type = "movie",
                name = data.name,
                poster = null,
                background = null,
                inWatchlist = true,
                addedAt = data.updatedAt,
                listStatus = data.listStatus,
                profileId = data.profileId,
            ),
        )
        progressDao.upsert(
            WatchProgressEntity(
                key = "shared:movie|v1",
                metaId = "shared:movie",
                videoId = "v1",
                type = "movie",
                season = null,
                episode = null,
                positionMs = data.positionMs,
                durationMs = 100_000L,
                watched = false,
                updatedAt = data.updatedAt,
                profileId = data.profileId,
            ),
        )
    }

    private suspend fun insertProfileSample(
        libraryRepo: LibraryRepositoryImpl,
        progressDao: FakeProgressDao,
        searchRepo: SearchHistoryRepositoryImpl,
        data: ProfileSampleData,
    ) {
        libraryRepo.setStatus(
            testPreview(id = "${data.prefix}_watch", name = "${data.prefix.uppercase()} Watch"),
            data.listStatus,
        )
        progressDao.upsert(
            WatchProgressEntity(
                key = "${data.prefix}_hist|v1",
                metaId = "${data.prefix}_hist",
                videoId = "v1",
                type = "movie",
                season = null,
                episode = null,
                positionMs = 1000L,
                durationMs = 2000L,
                watched = true,
                updatedAt = 1000L * data.profileId,
                profileId = data.profileId,
            ),
        )
        searchRepo.save("${data.prefix}_srch")
    }
}
