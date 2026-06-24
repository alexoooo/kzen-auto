package tech.kzen.auto.server.objects.job

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.util.concurrent.ConcurrentHashMap


/**
 * The shared [JobControl] handed to every Worker in a run. The worker-facing surface is
 * [checkpoint] / [runBlockingIo] / [publishProgress]; the run driver ([JobExecution]) flips the phase via
 * [pause] / [resume] / [cancel].
 *
 * A parked worker suspends on a [CompletableDeferred] captured under the monitor (so a concurrent
 * [resume] / [cancel] can't be lost between the phase check and the await), then re-checks the phase on
 * wake-up. [cancel] surfaces as a [CancellationException] thrown from [checkpoint], unwinding the worker
 * for resource cleanup.
 *
 * [publishProgress] writes each Worker's live progress to the shared (thread-safe) [logicTraceHandle] under
 * a CHILD of the Worker's stable-id trace path ([progressSegment]), so it never collides with the terminal
 * status [JobExecution] writes at the bare stable-id path. Writes are throttled per location (see
 * [progressThrottleNanos]); a forced write (the final end-of-stream value) always lands.
 */
class JobControlImpl(
    private val logicTraceHandle: LogicTraceHandle,
    private val objectStableMapper: ObjectStableMapper
): JobControl {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Minimum spacing between (non-forced) progress writes per Worker, so a Worker emitting per small
        // batch doesn't flood the trace store / the UI poll.
        private const val progressThrottleNanos = 200_000_000L  // 200 ms
    }
    //-----------------------------------------------------------------------------------------------------------------
    private enum class Phase {
        Running,
        Pausing,
        Cancelling
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val monitor = Any()
    private var phase = Phase.Running
    private var releaseSignal: CompletableDeferred<Unit>? = null

    private val lastProgressNanos = ConcurrentHashMap<ObjectLocation, Long>()


    //-----------------------------------------------------------------------------------------------------------------
    fun pause() {
        synchronized(monitor) {
            if (phase == Phase.Running) {
                phase = Phase.Pausing
                if (releaseSignal == null) {
                    releaseSignal = CompletableDeferred()
                }
            }
        }
    }


    fun resume() {
        synchronized(monitor) {
            if (phase == Phase.Cancelling) {
                return
            }
            phase = Phase.Running
            releaseSignal?.complete(Unit)
            releaseSignal = null
        }
    }


    fun cancel() {
        synchronized(monitor) {
            phase = Phase.Cancelling
            releaseSignal?.complete(Unit)
            releaseSignal = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun checkpoint() {
        while (true) {
            val signal = synchronized(monitor) {
                when (phase) {
                    Phase.Running ->
                        return

                    Phase.Cancelling ->
                        throw CancellationException("Job cancelled")

                    Phase.Pausing ->
                        releaseSignal ?: CompletableDeferred<Unit>().also { releaseSignal = it }
                }
            }
            signal.await()
        }
    }


    override suspend fun <R> runBlockingIo(block: () -> R): R {
        // M1: run inline on the counting-dispatcher thread so the work stays visible to quiescence
        // detection (the thread is occupied → inFlight stays positive). A future refinement may offload to
        // a separate *counted* IO pool so a blocking worker doesn't occupy a compute thread.
        return block()
    }


    override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
        val now = System.nanoTime()
        if (! force) {
            val last = lastProgressNanos[location]
            if (last != null && now - last < progressThrottleNanos) {
                return
            }
        }
        lastProgressNanos[location] = now

        val tracePath = JobConventions.workerProgressPath(objectStableMapper.objectStableId(location))
        logicTraceHandle.set(tracePath, ExecutionValue.of(value))
    }
}
