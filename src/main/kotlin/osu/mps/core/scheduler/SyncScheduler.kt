package osu.mps.core.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration

abstract class SyncScheduler(
    private val period: Duration,
    private val runAtStart: Boolean = true,
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val logger = LoggerFactory.getLogger(javaClass)

    fun start() {
        if (loop != null) return
        loop = scope.launch {
            var shouldRun = runAtStart
            while (isActive) {
                if (shouldRun) {
                    try {
                        runOnce()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A transient database/API failure must not terminate the scheduler loop.
                        logger.error("Scheduled run failed; it will be retried after $period", e)
                    }
                }
                shouldRun = true
                delay(period)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    abstract suspend fun runOnce()
}
