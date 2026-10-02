package osu.mps.core.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

abstract class SyncScheduler(
    private val period: Duration,
    private val runAtStart: Boolean = true,
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    fun start() {
        if (loop != null) return
        loop = scope.launch {
            if (runAtStart) {
                runOnce()
            }
            while (isActive) {
                delay(period)
                runOnce()
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    abstract suspend fun runOnce()
}