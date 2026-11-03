package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.TorrentFailReason
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class MetadataRegistry {
    private val map = ConcurrentHashMap<String, CompletableDeferred<TorrentMetadata>>()

    fun register(hash: String): CompletableDeferred<TorrentMetadata> {
        val key = hash.lowercase()
        return map.computeIfAbsent(key) { CompletableDeferred() }
    }

    fun complete(
        hash: String,
        metadata: TorrentMetadata,
    ) {
        val key = hash.lowercase()
        map[key]?.complete(metadata)
    }

    fun fail(
        hash: String,
        error: TorrentBackendException,
    ) {
        val key = hash.lowercase()
        map[key]?.completeExceptionally(error)
    }

    suspend fun await(hash: String): TorrentMetadata {
        val key = hash.lowercase()
        val deferred = map[key] ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
        return deferred.await()
    }

    fun remove(hash: String) {
        val key = hash.lowercase()
        val deferred = map.remove(key)
        deferred?.completeExceptionally(TorrentBackendException(TorrentFailReason.STORAGE))
    }

    fun has(hash: String): Boolean {
        val key = hash.lowercase()
        return map.containsKey(key)
    }
}
