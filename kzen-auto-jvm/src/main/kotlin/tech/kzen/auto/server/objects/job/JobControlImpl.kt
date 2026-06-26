package tech.kzen.auto.server.objects.job

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.JobLogicHost
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.util.concurrent.ConcurrentHashMap


/**
 * The shared [JobControl] handed to every Worker in a run. The worker-facing surface is
 * [checkpoint] / [runBlockingIo] / [publishProgress]; the run driver ([JobExecution]) flips the phase via
 * [pause] / [resume] / [step] / [cancel].
 *
 * A parked worker suspends on a [CompletableDeferred] captured under the monitor (so a concurrent
 * [resume] / [step] / [cancel] can't be lost between the phase check and the await). Being woken means
 * "proceed past this checkpoint": [resume] then runs free (phase is Running), while [step] keeps the Job
 * paused but completes the captured signal once and leaves a FRESH (uncompleted) one for the next checkpoint,
 * so each parked worker advances exactly one checkpoint (one wavefront) and re-parks on its own. [cancel]
 * surfaces as a [CancellationException] thrown from [checkpoint], unwinding the worker for resource cleanup.
 *
 * [publishProgress] writes each Worker's live progress to the shared (thread-safe) [logicTraceHandle] under
 * a CHILD of the Worker's stable-id trace path ([progressSegment]), so it never collides with the terminal
 * status [JobExecution] writes at the bare stable-id path. Writes are throttled per location (see
 * [progressThrottleNanos]); a forced write (the final end-of-stream value) always lands.
 */
class JobControlImpl(
    private val logicTraceHandle: LogicTraceHandle,
    private val objectStableMapper: ObjectStableMapper,
    private val logicHost: JobLogicHost
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

    // Set when a nested-Logic Worker pauses the run via [requestErrorPause] (pause-on-error), so the driver
    // ([JobExecution]) can distinguish an error pause from genuine deadlock when the run quiesces, and report
    // it as paused rather than failed. Read + cleared by the driver ([consumeErrorPause]); also cleared on any
    // controller-initiated phase change so a clean resume / step / cancel starts fresh. Guarded by [monitor].
    private var errorPause = false

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
            errorPause = false
            releaseSignal?.complete(Unit)
            releaseSignal = null
        }
    }


    // Release each currently-parked Worker past exactly one checkpoint, but STAY paused: completing the
    // captured signal wakes the parked Workers, and nulling it (without leaving Running) means each re-parks
    // at its NEXT checkpoint on a fresh signal. So one step advances one quiescent wavefront rather than
    // resuming the run — the run driver awaits quiescence after this to let the wavefront settle.
    fun step() {
        synchronized(monitor) {
            if (phase == Phase.Cancelling) {
                return
            }
            phase = Phase.Pausing
            errorPause = false
            releaseSignal?.complete(Unit)
            releaseSignal = null
        }
    }


    fun cancel() {
        synchronized(monitor) {
            phase = Phase.Cancelling
            errorPause = false
            releaseSignal?.complete(Unit)
            releaseSignal = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun requestErrorPause() {
        synchronized(monitor) {
            // Only a free-running Job becomes an error pause: while already Pausing (a plain pause or a step
            // wavefront descending into a child) a child's paused result is the normal stepping mechanism, not
            // an error — leave the phase and flag untouched so it is driven onward, not reported as paused.
            if (phase == Phase.Running) {
                phase = Phase.Pausing
                errorPause = true
                if (releaseSignal == null) {
                    releaseSignal = CompletableDeferred()
                }
            }
        }
    }


    // Whether a Worker has requested an error pause since the last resume / step (read by the driver to report
    // the quiesced run as paused rather than deadlocked).
    fun isErrorPausePending(): Boolean {
        return synchronized(monitor) { errorPause }
    }


    // Read + clear the error-pause flag (the driver clears it as it reports the pause, so the next resume
    // starts clean even though resume() also clears it defensively).
    fun consumeErrorPause(): Boolean {
        return synchronized(monitor) {
            val pending = errorPause
            errorPause = false
            pending
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun checkpoint() {
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

        // Park until released. Only [resume] / [step] / [cancel] complete the captured signal, so being woken
        // authorizes proceeding past THIS checkpoint (a [pause] never completes a signal — it only arms one).
        // A [step] keeps the phase Pausing and nulls the signal, so the NEXT checkpoint re-parks on a fresh
        // one: exactly one wavefront per step. Re-check for cancel on wake so a cancel still unwinds the Worker.
        signal.await()

        if (synchronized(monitor) { phase == Phase.Cancelling }) {
            throw CancellationException("Job cancelled")
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


    override fun logicHost(): JobLogicHost {
        return logicHost
    }
}
