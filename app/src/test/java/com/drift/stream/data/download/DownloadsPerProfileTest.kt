package com.drift.stream.data.download

import com.drift.stream.data.torrent.NetworkStatus
import com.drift.stream.domain.model.DownloadItem
import com.drift.stream.domain.model.DownloadState
import com.drift.stream.domain.model.EnqueueResult
import com.drift.stream.domain.model.PauseReason
import com.drift.stream.testutil.FakeActiveProfileProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsPerProfileTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createSetup(
        scope: kotlinx.coroutines.CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Pair<DefaultDownloadManager, DownloadTestDependencies> {
        val activeProfile = FakeActiveProfileProvider(1L)
        val fileStore = FakeDownloadFileStore(tmpFolder.root)
        val deps =
            DownloadTestDependencies(
                activeProfile = activeProfile,
                fileStore = fileStore,
                repo = FakeDownloadRepository(activeProfile),
            )
        val manager =
            createTestDownloadManager(
                scope = scope,
                dispatcher = dispatcher,
                deps = deps,
            )
        return Pair(manager, deps)
    }

    @Test
    fun DL1_observeAll_isPerProfile() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item1 = createTestDownloadItem("d1").copy(profileId = 1L)
            val item2 = createTestDownloadItem("d2").copy(profileId = 2L)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(1L)
            val p1Items = manager.items.first()
            assertEquals(1, p1Items.size)
            assertEquals("d1", p1Items[0].id)

            deps.activeProfile.setActive(2L)
            val p2Items = manager.items.first()
            assertEquals(1, p2Items.size)
            assertEquals("d2", p2Items[0].id)
        }

    @Test
    fun DL2_observeForMeta_isPerProfile() =
        runTest {
            val (_, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item1 = createTestDownloadItem("d1").copy(metaId = "meta_shared", profileId = 1L)
            val item2 = createTestDownloadItem("d2").copy(metaId = "meta_shared", profileId = 2L)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(1L)
            val p1ForMeta = deps.repo.observeForMeta("meta_shared").first()
            assertEquals(1, p1ForMeta.size)
            assertEquals("d1", p1ForMeta[0].id)

            deps.activeProfile.setActive(2L)
            val p2ForMeta = deps.repo.observeForMeta("meta_shared").first()
            assertEquals(1, p2ForMeta.size)
            assertEquals("d2", p2ForMeta[0].id)
        }

    @Test
    fun DL3_enqueue_setsTheActiveProfileId() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.activeProfile.setActive(2L)

            val result = manager.enqueue(testRequest(metaId = "m2", videoId = "v2"))
            assertTrue(result is EnqueueResult.Added)
            val enqueuedId = (result as EnqueueResult.Added).id

            val item = deps.repo.get(enqueuedId)
            assertNotNull(item)
            assertEquals(2L, item?.profileId)
        }

    @Test
    fun DL4_engineGetAll_seesEveryProfile() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item1 = createTestDownloadItem("d1").copy(profileId = 1L)
            val item2 = createTestDownloadItem("d2").copy(profileId = 2L)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(1L)
            val allFromRepo = deps.repo.getAll()
            assertEquals(2, allFromRepo.size)

            val allFromManager = manager.allItems.first()
            assertEquals(2, allFromManager.size)
        }

    @Test
    fun DL5_deleteCompleted_touchesOnlyTheActiveProfile() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item1 = createTestDownloadItem("d1", state = DownloadState.COMPLETED).copy(profileId = 1L)
            val item2 = createTestDownloadItem("d2", state = DownloadState.COMPLETED).copy(profileId = 2L)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(2L)
            manager.deleteCompleted()
            advanceUntilIdle()

            val remaining = deps.repo.getAll()
            assertEquals(1, remaining.size)
            assertEquals("d1", remaining[0].id)
            assertEquals(1L, remaining[0].profileId)
        }

    @Test
    fun DL6_pauseAllAndResumeAll_touchOnlyTheActiveProfile() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ ->
                kotlinx.coroutines.CompletableDeferred<com.drift.stream.domain.repository.HttpResult>().await()
            }

            val item1 = createTestDownloadItem("d1", state = DownloadState.DOWNLOADING).copy(profileId = 1L)
            val item2 = createTestDownloadItem("d2", state = DownloadState.DOWNLOADING).copy(profileId = 2L)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(2L)
            manager.pauseAll()
            advanceUntilIdle()

            // Profile 1's download was not paused by profile 2's pauseAll
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get("d1")?.state)
            assertEquals(DownloadState.PAUSED, deps.repo.get("d2")?.state)
            assertEquals(PauseReason.USER, deps.repo.get("d2")?.pauseReason)

            manager.resumeAll()
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get("d1")?.state)
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get("d2")?.state)
        }

    @Test
    fun DL7_waitingForWifiResume_coversEveryProfile() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler =
                {
                    _,
                    _,
                    ->
                    kotlinx.coroutines.CompletableDeferred<com.drift.stream.domain.repository.HttpResult>().await()
                }
            deps.prefs.wifiOnly.value = true
            deps.network.statusFlow.value = NetworkStatus.METERED

            val item1 =
                createTestDownloadItem("d1", state = DownloadState.WAITING_FOR_WIFI)
                    .copy(profileId = 1L, allowMetered = false)
            val item2 =
                createTestDownloadItem("d2", state = DownloadState.WAITING_FOR_WIFI)
                    .copy(profileId = 2L, allowMetered = false)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(1L)

            // Wi-Fi returns
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            advanceUntilIdle()

            val updated1 = deps.repo.get("d1")
            val updated2 = deps.repo.get("d2")
            assertTrue(
                "Expected d1 to resume, state=${updated1?.state}",
                updated1?.state == DownloadState.QUEUED || updated1?.state == DownloadState.DOWNLOADING,
            )
            assertTrue(
                "Expected d2 to resume across profile boundaries, state=${updated2?.state}",
                updated2?.state == DownloadState.QUEUED || updated2?.state == DownloadState.DOWNLOADING,
            )
        }

    @Test
    fun DL9_service_usesAllItems_whenTheActiveProfileChanges() =
        runTest {
            val (manager, deps) = createSetup(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item1 = createTestDownloadItem("d1").copy(profileId = 1L)
            val item2 = createTestDownloadItem("d2").copy(profileId = 2L)
            deps.repo.insert(item1)
            deps.repo.insert(item2)

            deps.activeProfile.setActive(1L)
            assertEquals(2, manager.allItems.first().size)

            deps.activeProfile.setActive(2L)
            assertEquals(2, manager.allItems.first().size)
        }
}
