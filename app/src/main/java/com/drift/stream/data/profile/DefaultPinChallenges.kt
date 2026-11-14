package com.drift.stream.data.profile

import com.drift.stream.domain.profile.CHOOSE_TIMEOUT_MS
import com.drift.stream.domain.profile.PendingPin
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.PinRequest
import com.drift.stream.domain.profile.PinResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val SAFETY_TIMEOUT_EXTRA_MS = 1000L

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
        private var currentRequest: PinRequest? = null
        private var heldTimeoutJob: Job? = null
        private var heldCompletionHandle: DisposableHandle? = null

        override suspend fun ask(
            request: PinRequest,
            holdUntilReleased: Boolean,
        ): PinResult {
            _pending.first { it == null }

            return mutex.withLock {
                val deferred = CompletableDeferred<PinResult>()
                val id = idGenerator.getAndIncrement()
                currentDeferred = deferred
                currentId = id
                currentRequest = request
                _pending.value = PendingPin(id, request, holdUntilReleased)

                val callerJob = coroutineContext[Job]
                val result =
                    try {
                        deferred.await()
                    } catch (e: CancellationException) {
                        clearState(id)
                        throw e
                    }

                if (holdUntilReleased && result is PinResult.Verified) {
                    val handle =
                        callerJob?.invokeOnCompletion { cause ->
                            if (cause != null) {
                                release(id)
                            }
                        }
                    heldCompletionHandle = handle

                    val timeoutContext = coroutineContext.minusKey(Job) + SupervisorJob()
                    heldTimeoutJob =
                        CoroutineScope(timeoutContext).launch {
                            delay(CHOOSE_TIMEOUT_MS + SAFETY_TIMEOUT_EXTRA_MS)
                            release(id)
                        }

                    result
                } else {
                    clearState(id)
                    result
                }
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

        override fun release(id: Long) {
            clearState(id)
        }

        private fun clearState(id: Long) {
            val reqId = currentId
            val profileId = (currentRequest as? PinRequest.Verify)?.profileId
            if (id == reqId || (profileId != null && id == profileId)) {
                heldTimeoutJob?.cancel()
                heldTimeoutJob = null
                heldCompletionHandle?.dispose()
                heldCompletionHandle = null
                _pending.value = null
                currentDeferred = null
                currentId = null
                currentRequest = null
            }
        }
    }
