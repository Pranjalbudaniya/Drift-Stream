package com.drift.stream.data.profile

import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPinChallenges
    @Inject
    constructor() : PinChallenges {
        private val mutex = Mutex()
        private val idGenerator = AtomicLong(1L)

        private val _pending = MutableStateFlow<PendingPin?>(null)
        override val pending: StateFlow<PendingPin?> = _pending.asStateFlow()

        private var currentDeferred: CompletableDeferred<PinResult>? = null
        private var currentId: Long? = null

        override suspend fun ask(request: PinRequest): PinResult =
            mutex.withLock {
                val deferred = CompletableDeferred<PinResult>()
                val id = idGenerator.getAndIncrement()
                currentDeferred = deferred
                currentId = id
                _pending.value = PendingPin(id, request)

                try {
                    deferred.await()
                } finally {
                    _pending.value = null
                    currentDeferred = null
                    currentId = null
                }
            }

        override fun finish(
            id: Long,
            result: PinResult,
        ) {
            if (currentId == id) {
                currentDeferred?.complete(result)
            }
        }
    }
