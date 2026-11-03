package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.TorrentFailReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataRegistryTest {
    private val sampleMetadata =
        TorrentMetadata(
            files = listOf(BackendFile(0, "movie.mp4", 1000L, 0L)),
            pieceLength = 16384L,
        )

    @Test
    fun MR1_awaitAfterRegister_completesWhenMetadataArrives() =
        runTest {
            val registry = MetadataRegistry()
            registry.register("hash1")
            val deferred = async { registry.await("hash1") }

            registry.complete("hash1", sampleMetadata)

            val result = deferred.await()
            assertEquals(sampleMetadata, result)
        }

    @Test
    fun MR2_metadataBeforeAwait_isNotLost() =
        runTest {
            val registry = MetadataRegistry()
            registry.register("hash2")
            registry.complete("hash2", sampleMetadata)

            val result = registry.await("hash2")
            assertEquals(sampleMetadata, result)
        }

    @Test
    fun MR3_awaitUnregistered_throwsTheEngineFailure() =
        runTest {
            val registry = MetadataRegistry()
            try {
                registry.await("unregistered_hash")
                fail("Expected TorrentBackendException")
            } catch (e: TorrentBackendException) {
                assertEquals(TorrentFailReason.STORAGE, e.reason)
            }
        }

    @Test
    fun MR4_registerTwice_returnsTheSameDeferred() =
        runTest {
            val registry = MetadataRegistry()
            val d1 = registry.register("hash4")
            val d2 = registry.register("hash4")
            assertSame(d1, d2)

            val await1 = async { registry.await("hash4") }
            val await2 = async { registry.await("hash4") }

            registry.complete("hash4", sampleMetadata)

            assertEquals(sampleMetadata, await1.await())
            assertEquals(sampleMetadata, await2.await())
        }

    @Test
    fun MR5_remove_failsPendingAwaitersWithTheEngineFailure() =
        runTest {
            val registry = MetadataRegistry()
            registry.register("hash5")

            var caughtException: Throwable? = null
            val job =
                launch {
                    try {
                        registry.await("hash5")
                    } catch (t: Throwable) {
                        caughtException = t
                    }
                }

            registry.remove("hash5")
            job.join()

            assertTrue(caughtException is TorrentBackendException)
            assertEquals(TorrentFailReason.STORAGE, (caughtException as TorrentBackendException).reason)

            try {
                registry.await("hash5")
                fail("Expected TorrentBackendException for later await")
            } catch (e: TorrentBackendException) {
                assertEquals(TorrentFailReason.STORAGE, e.reason)
            }
        }

    @Test
    fun MR6_completeForUnknownHash_isIgnored() {
        val registry = MetadataRegistry()
        registry.complete("unknown", sampleMetadata)
        assertFalse(registry.has("unknown"))
    }
}
