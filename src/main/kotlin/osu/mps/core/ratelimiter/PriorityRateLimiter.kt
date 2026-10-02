package osu.mps.core.ratelimiter

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class PriorityRateLimiter(
    private val minIntervalMs: Long,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val mutex = Mutex()
    private val queue = PriorityQueue<WaitEntry>()
    private val nextOrder = AtomicLong(0L)
    private var lastRequestAtMs = 0L
    private var processorRunning = false

    suspend fun acquire(priority: RequestPriority = RequestPriority.Normal) {
        val entry = WaitEntry(
            priority = priority,
            order = nextOrder.getAndIncrement(),
            deferred = CompletableDeferred(),
        )

        mutex.withLock {
            queue.add(entry)
            ensureProcessorLocked()
        }

        try {
            entry.deferred.await()
        } catch (e: CancellationException) {
            mutex.withLock { queue.remove(entry) }
            throw e
        }
    }

    private fun ensureProcessorLocked() {
        if (processorRunning) return
        processorRunning = true
        scope.launch {
            try {
                processLoop()
            } finally {
                mutex.withLock {
                    processorRunning = false
                    if (queue.isNotEmpty()) {
                        ensureProcessorLocked()
                    }
                }
            }
        }
    }

    private suspend fun processLoop() {
        while (true) {
            val waitMs = mutex.withLock {
                if (queue.isEmpty()) return
                (lastRequestAtMs + minIntervalMs - System.currentTimeMillis()).coerceAtLeast(0)
            }

            if (waitMs > 0) {
                delay(waitMs.milliseconds)
            }

            val deferred = mutex.withLock {
                val entry = queue.poll() ?: return
                lastRequestAtMs = System.currentTimeMillis()
                entry.deferred
            }

            deferred.complete(Unit)
        }
    }

    private data class WaitEntry(
        val priority: RequestPriority,
        val order: Long,
        val deferred: CompletableDeferred<Unit>,
    ) : Comparable<WaitEntry> {
        override fun compareTo(other: WaitEntry): Int {
            val byPriority = other.priority.compareTo(priority)
            if (byPriority != 0) return byPriority
            return order.compareTo(other.order)
        }
    }
}
