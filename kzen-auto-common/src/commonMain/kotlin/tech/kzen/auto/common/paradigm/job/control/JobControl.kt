package tech.kzen.auto.common.paradigm.job.control

import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Job-level cooperative synchronization handle, shared by every Worker in a running Job. Generalizes the
 * single-threaded LogicControl to the concurrent setting: Workers run at full speed in parallel and only
 * coordinate at the safe points they declare by calling [checkpoint].
 */
interface JobControl {
    /**
     * Cooperative safe point, called at message boundaries (and inside long compute loops). Returns
     * immediately while the Job is running; parks the calling Worker while the Job is paused / stepping;
     * throws a CancellationException while the Job is cancelling (unwinding the Worker so its resources
     * are released).
     */
    suspend fun checkpoint()


    /**
     * Runs a blocking IO [block] such that it stays visible to the Job's quiescence detection. Workers
     * MUST route blocking IO through this rather than offloading to an uncounted dispatcher (e.g. a bare
     * `withContext(Dispatchers.IO)`), which would read as false quiescence and spuriously pause the Job.
     */
    suspend fun <R> runBlockingIo(block: () -> R): R


    /**
     * An absolute filesystem path (a String — [JobControl] stays platform-neutral, so no JVM `Path` in the SPI)
     * to a private scratch directory this Worker may use for file-backed operator state (an on-disk pivot store,
     * an indexed CSV table). The directory is created on first call. It is unique per (run, Worker) and
     * deterministic across a live-edit migrate — the run id is migrate-stable, so a rebuilt Worker resolves the
     * SAME path (the P4 baseline restarts a file-backed operator on an edit, coherent precisely because the path
     * is stable). The whole run tree is swept when the run settles, and a stale tree is cleared on the next
     * process's boot sweep after a hard kill.
     */
    fun scratchDir(): String


    /**
     * Publishes a Worker's live progress (e.g. row counts, or a preview sample) to its own trace, keyed by
     * [location] (the Worker's own [ObjectLocation]), for the interactive UI to poll while the Job runs.
     * This is the always-on PUSH path — distinct from the on-demand request/reply a Worker may serve over a
     * duplex Channel. [value] is an arbitrary structured map (string keys; string / number / boolean / list
     * / nested-map values).
     *
     * Writes are throttled per [location] so a Worker emitting per-(small-)batch does not flood the trace;
     * pass [force] = true for the final value at end-of-stream so it is never dropped by the throttle.
     */
    fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean = false)


    /**
     * Invoke another Logic ([instructions] — a Script / Flow / Job) as a confined child, binding [input] as its
     * first declared parameter (the single-positional convention shared with a Script Run step and a Flow
     * Run-Logic vertex), and return its output tuple. The seam that lets a Worker compose reusable sub-Logics
     * into a Job's dataflow — a Run Worker running a child per incoming element.
     *
     * The engine drives the child's stepping and coordinates pause / cancel centrally across the whole run, so
     * a child that halts — a Pause step, or a recoverable failure parked under pause-on-error — leaves this call
     * suspended and brings the whole Job to a quiescent paused wavefront for inspect / fix + resume; on resume
     * the same child is driven onward. No explicit halt request is needed (unlike the old re-entrant executor):
     * a child breakpoint IS a run-wide pause. Only nested-Logic Workers (e.g.
     * [RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker]) call this.
     */
    suspend fun host(instructions: ObjectLocation, input: Any?): TupleValue
}
