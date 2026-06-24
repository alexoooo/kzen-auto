package tech.kzen.auto.server.objects.job

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext


/**
 * The load-bearing primitive of Job execution: an N-thread [CoroutineDispatcher] that counts in-flight
 * dispatch tasks. A worker coroutine suspended on a channel send/receive or parked at a `checkpoint`
 * contributes **zero** (its dispatch task has returned to the pool), so `inFlight == 0` is exactly the
 * **quiescent wavefront** — every worker suspended at a message boundary or done.
 *
 * That one signal powers the pause barrier (await quiescence, then return `LogicResultPaused`), the global
 * step tick (release, await quiescence, re-arm pause), and deadlock detection (quiescent while running and
 * not all terminated). The await methods block the *controller-execution thread* (not a coroutine), which
 * the [tech.kzen.auto.server.service.impl.ServerLogicController] runs outside its monitor, so blocking here
 * is safe.
 */
class CountingDispatcher(
    parallelism: Int
):
    CoroutineDispatcher()
{
    //-----------------------------------------------------------------------------------------------------------------
    private val executor = Executors.newFixedThreadPool(parallelism) { runnable ->
        Thread(runnable, "kzen-job-worker").apply {
            isDaemon = true
        }
    }

    private val lock = ReentrantLock()
    private val quiescentCondition = lock.newCondition()
    private var inFlight = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        lock.withLock {
            inFlight++
        }
        executor.execute {
            try {
                block.run()
            }
            finally {
                lock.withLock {
                    inFlight--
                    if (inFlight == 0) {
                        quiescentCondition.signalAll()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Block the calling (non-coroutine) thread until no dispatch tasks are queued or running — i.e. every
     * worker is suspended at a message boundary / checkpoint, or has terminated.
     */
    fun awaitQuiescent() {
        lock.withLock {
            while (inFlight != 0) {
                quiescentCondition.await()
            }
        }
    }


    /**
     * Block up to [timeoutMillis] for quiescence, so the caller can periodically re-poll for pause/cancel
     * while workers are still progressing.
     * @return true if quiescence (`inFlight == 0`) was reached, false on timeout.
     */
    fun awaitQuiescenceOrProgress(timeoutMillis: Long): Boolean {
        lock.withLock {
            if (inFlight == 0) {
                return true
            }
            quiescentCondition.await(timeoutMillis, TimeUnit.MILLISECONDS)
            return inFlight == 0
        }
    }


    fun isQuiescent(): Boolean {
        return lock.withLock { inFlight == 0 }
    }


    fun shutdown() {
        executor.shutdownNow()
    }
}
