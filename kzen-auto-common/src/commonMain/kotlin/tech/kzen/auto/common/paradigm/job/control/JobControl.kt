package tech.kzen.auto.common.paradigm.job.control

import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


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
     * An absolute filesystem path (a String — see [scratchDir]) to this Worker's PERSISTENT, per-Worker output
     * directory. Unlike [scratchDir] this is NOT transient run state: it is keyed on the Worker's NOTATION
     * identity (its [ObjectLocation]) rather than the run, so it SURVIVES the run settling — the result a
     * file-backed sink accumulates (an Explore [IndexedCsvTable]) can be browsed / downloaded AFTER the run
     * ends, which is what makes a Job usable for reporting. Semantics are last-run-wins: a new run of the same
     * Worker replaces it, and it is never swept by the run-settle / boot cleanup that clears [scratchDir] trees.
     *
     * Also unlike [scratchDir], the directory is NOT auto-created — a persisting Worker owns its lifecycle
     * (typically clearing it at run start so the new run fully replaces the previous). Only a Worker that
     * persists output ([tech.kzen.auto.server.objects.job.worker.ExploreWorker]) calls this; the default throws.
     */
    fun outputDir(): String =
        throw UnsupportedOperationException("This Worker has no persistent output directory")


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
     * The declared parameters of this Job — its typed input signature, derived from the document's `parameters`
     * branch of ParameterBinding declarations (see
     * [tech.kzen.auto.common.objects.document.job.JobSignatureCapability]). A Worker that compiles user
     * expressions builds its typed parameter scope from this (name + TypeMetadata per component) with the values
     * read via [parameter] — generic, so a third-party Worker gets the same scope with no framework change.
     * Default empty: an environment without argument binding.
     */
    fun parameters(): TupleDefinition = TupleDefinition.empty


    /**
     * The declared results of this Job — its typed output signature, derived from the document's `results`
     * signature map (see [tech.kzen.auto.common.objects.document.job.JobSignatureCapability]). A ResultSink
     * Worker reads its own component's declared type from this (e.g. nullability decides whether an empty
     * stream is a failure or a null result) — generic, so a third-party sink gets the same contract with no
     * framework change. Default empty: an environment without result harvesting.
     */
    fun results(): TupleDefinition = TupleDefinition.empty


    /**
     * The statically inferred payload type of this Worker's INPUT lane (each Worker gets its own control —
     * see EngineJobControl), derived by the server's payload-type walk over the wiring (the same
     * per-notation-version inference the editor's worker cards display, so runtime and display cannot
     * drift). Null when the lane carries no static payload type (a flat/CSV lane, or an untyped source) —
     * an expression-compiling Worker then falls back to a nullable `Any` receiver. Generic, so a
     * third-party Worker builds the same typed expression scope with no framework change. Default null:
     * an environment without inference.
     */
    fun payloadType(): TypeMetadata? = null


    /**
     * The value bound to the declared Job parameter [name]: the run argument the caller passed, falling back to
     * the declaration's typed `default` when the run supplies none (Script parity). Null when neither exists (or
     * the argument is null): a parameterized Job run bare still executes, its expressions seeing null. Values are
     * run-constant — a live-edit migrate re-supplies the same arguments — so no carry is needed here (a streaming
     * source carries only its own position). Default null: an environment without argument binding.
     */
    fun parameter(name: String): Any? = null


    /**
     * Contribute a named component to this Job run's output tuple (the result a host — a Script RunStep, a Flow
     * Run vertex, a Job RunWorker — receives when the run completes). Harvested once the run settles; last write
     * per [component] wins, so a re-yield after a live-edit migrate is idempotent. The conventional component is
     * "main" (the hosts' single-positional harvest reads it); a Job with several result sinks yields several named
     * components. Default no-op: an environment without result harvesting.
     */
    fun yieldResult(component: String, value: Any?) {}


    /** Append one immutable structured event to this Worker's run history. */
    fun log(location: ObjectLocation, value: Map<String, Any?>) {}


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


    /**
     * Invoke a child with named arguments. Compatibility controls can support a single component by delegating
     * to the positional overload; a real engine overrides this for arbitrary named tuples.
     */
    suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue {
        check(arguments.components.size == 1) {
            "Named child hosting is unsupported by this JobControl for ${arguments.components.size} arguments"
        }
        return host(instructions = instructions, input = arguments.components.single().value)
    }
}
