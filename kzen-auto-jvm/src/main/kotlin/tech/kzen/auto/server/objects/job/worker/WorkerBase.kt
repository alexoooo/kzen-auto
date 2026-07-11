package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Shared base for the Job Workers' framework-driven execution strategy. It OWNS the cross-cutting concerns a
 * raw [Worker] used to hand-roll — and silently get wrong: the run scaffolding, end-of-stream propagation
 * (closing the output so the downstream loop terminates), the cooperative [JobControl.checkpoint] per unit of
 * work, throttled progress publication, and the duplex serve loop. A subclass supplies only its business
 * logic via the lifecycle hooks; it never writes `try/finally { output.close() }`, a `checkpoint()` loop, an
 * `item as T` cast, or a `coroutineScope { launch { … } }` serve loop again.
 *
 * This is OPT-IN convenience, not the only contract: the raw [Worker] SPI stays first-class for a Worker that
 * must own its own execution (a Guava-Service-style background thread / pool), drive a nested Logic, or
 * otherwise step outside the simple "drain input, emit output" shape. The hook names deliberately mirror the
 * AsyncWorker lifecycle (onStart ≈ init, [drive] ≈ work, onClose ≈ close) so such a Worker can later be hosted
 * by a self-managed executor running the same logic.
 *
 * INTERACTIVITY is unified on a single immutable [snapshot]: after each unit of work the framework captures it
 * (when a [serve] port is present) into one `@Volatile` reference, and derives the [progress] trace payload
 * from the same value. The push path (progress) and the pull path ([onQuery]) therefore read the SAME captured
 * state — the Worker's own mutable state stays confined to the work coroutine, and only the immutable snapshot
 * crosses to the serve coroutine, so no Worker hand-rolls `@Volatile` sharing.
 *
 * Run-scoped state lives in subclass instance fields (the Worker instance is constructed once and its [run]
 * executes once per Job, parking at [JobControl.checkpoint] while paused), which is what makes the
 * state-migration (pause / edit config / continue) handoff possible — see [loadState].
 */
abstract class WorkerBase(
    private val selfLocation: ObjectLocation,
    private val serve: ChannelServer<Any?, Any?>? = null
):
    Worker
{
    @Volatile
    private var latestSnapshot: Any? = null


    //-----------------------------------------------------------------------------------------------------------------
    final override suspend fun run(control: JobControl): Unit = coroutineScope {
        val serveChannel = serve
        val serveJob =
            if (serveChannel == null) {
                null
            }
            else {
                launch {
                    for (served in serveChannel) {
                        served.reply(onQuery(served.request, latestSnapshot))
                    }
                }
            }

        try {
            onStart(control)
            drive(control)
            publish(control, force = true)
        }
        finally {
            serveJob?.cancel()
            onClose()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Drives the Worker's input/output pattern — supplied by [SourceWorker] / [TransformWorker] / [SinkWorker]. */
    protected abstract suspend fun drive(control: JobControl)


    /** Run-scoped setup before the first unit of work (e.g. open a file). Default no-op. */
    protected open suspend fun onStart(control: JobControl) {}


    /** Deterministic cleanup after the run ends — completion, failure, or cancel alike. Default no-op. */
    protected open fun onClose() {}


    /**
     * Snapshot this Worker's run-scoped state for migration into a rebuilt instance, when a pause / edit-config
     * / continue rebuilds the Job's graph (see [tech.kzen.auto.server.objects.job.JobExecution]). Called on the
     * OUTGOING instance while it is parked at a checkpoint (quiescent) and BEFORE the run is torn down — so the
     * snapshot may include a LIVE resource (e.g. an open file reader) that the teardown's [onClose] would
     * otherwise close: such a capture should DETACH the resource so [onClose] skips it, transferring ownership
     * to the returned state. Default: null (nothing migrates → the worker restarts from scratch with the edited
     * config — the safe default, and the only coherent one for a sink that re-truncates).
     *
     * If the returned state holds a detached resource it should be [AutoCloseable]: [JobExecution] closes any
     * captured state whose Worker was REMOVED by the edit (so a detached handle can't leak), and a Worker whose
     * config changed incompatibly should likewise close it in [loadMigrationState] instead of adopting it.
     */
    internal open fun captureMigrationState(): Any? = null


    /** Adopt state captured by the previous (same stable id) instance's [captureMigrationState]. Default no-op. */
    internal open fun loadMigrationState(captured: Any?) {}


    /**
     * Immutable view of the Worker's current state, captured after each unit of work. Used for BOTH the
     * throttled [progress] push and the [onQuery] pull, so the two never diverge. Default none.
     */
    protected open fun snapshot(): Any? = null


    /**
     * Throttled live progress pushed to the Worker's trace (row counts, a teaser sample, …), derived from the
     * just-captured [snapshot] ([snapshot] is null unless overridden). Default none. Override the force-aware
     * variant below instead when the teaser and the final payload differ.
     */
    protected open fun progress(snapshot: Any?): Map<String, Any?>? = null


    /**
     * Force-aware variant of [progress]: force = true only on the final end-of-stream publish, letting a
     * Worker push a bounded teaser periodically but the full payload once at the end (every emit is retained
     * in engine history, so periodic pushes must be O(bounded) — see JobConventions.progressTeaserRowCount).
     * Defaults to the force-agnostic [progress]; override exactly one of the two.
     */
    protected open fun progress(snapshot: Any?, force: Boolean): Map<String, Any?>? = progress(snapshot)


    /** Answers one duplex request as a pure read of the latest [snapshot]. Default echoes the snapshot. */
    protected open fun onQuery(request: Any?, snapshot: Any?): Any? = snapshot


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Captures the snapshot for queries (when serving) and publishes the derived [progress] to the trace.
     * [force] reaches the progress hook (teaser vs final payload) as well as the publish throttle.
     */
    protected fun publish(control: JobControl, force: Boolean = false) {
        val snapshot = snapshot()
        if (serve != null && snapshot != null) {
            latestSnapshot = snapshot
        }

        val progressValue = progress(snapshot, force)
        if (progressValue != null) {
            control.publishProgress(selfLocation, progressValue, force)
        }
    }
}
