package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.nio.file.Files
import java.nio.file.Path


/**
 * The [JobControl] a single Job Worker runs against on the new engine — the per-Worker bridge from the Worker
 * SPI (which only ever touches [JobControl]) to the [Execution] of the Worker's own engine node. Each Worker is
 * hosted as its OWN confined node (see [JobRun]), so each gets its own [EngineJobControl] / [Execution]:
 * [checkpoint] parks THIS Worker independently and the engine brings every Worker to a quiescent wavefront
 * centrally. The old [tech.kzen.auto.server.objects.job.JobControlImpl]'s shared phase + release-signal are
 * therefore gone — the engine's run command IS the phase, and parking lives on the coroutine frame.
 *
 * [publishProgress] is the always-on PUSH path: it [Execution.emit]s the Worker's live progress at a reserved
 * marker address ([workerProgressAddressMarker]) so the controller's trace bridge routes it to
 * [tech.kzen.auto.common.objects.document.job.JobConventions.workerProgressPath] (the path the JS Job UI polls),
 * throttled per Worker so a per-(small-)batch publisher doesn't flood the trace history.
 *
 * [parameters] / [parameter] / [yieldResult] are the Job-signature seam (J2): any Worker reads a declared
 * parameter's bound run argument off the run's typed inputs (threaded in as [jobInputs] by [JobRun], falling back
 * to the declaration's default per [jobParameters]), and a ResultSink Worker yields a named output component into
 * the run's shared [resultCollector] — both generic, with no Worker-type knowledge here (see [JobRun]).
 *
 * [host] is the nested-Logic seam ([RunWorker][tech.kzen.auto.server.objects.job.worker.RunWorker]): it
 * compiles the child once via the run-shared [JobChildLogicHost] and hosts it under THIS Worker's own
 * [Execution] node, so the engine drives the child's stepping and pause / cancel uniformly with the rest of
 * the tree.
 */
class EngineJobControl(
    private val execution: Execution,
    private val childLogicHost: JobChildLogicHost,
    private val objectStableMapper: ObjectStableMapper,
    private val workerScratchDir: Path,
    private val workerOutputDir: Path,
    private val jobInputs: TupleValue,
    private val jobParameters: JobParameters,
    private val resultCollector: JobResultCollector
): JobControl {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Reserved emit address tagging a Worker's live-progress payload, distinct from any other value the
        // Worker node emits; the controller's trace bridge recognizes it and routes to the Worker's progress
        // trace path. A stable id can never collide (it is an ObjectLocation string).
        const val workerProgressAddressMarker = "\$job-progress"

        // Minimum spacing between (non-forced) progress emits per Worker, mirroring JobControlImpl — a forced
        // write (the final end-of-stream value) always lands.
        private const val progressThrottleNanos = 200_000_000L  // 200 ms
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val progressAddress = Address.of(workerProgressAddressMarker)

    // This Worker runs single-threaded on its own node coroutine, so a plain field suffices (no concurrent
    // publishProgress for one Worker).
    private var lastProgressNanos = 0L

    // Created lazily on the first scratchDir() call (single-threaded per Worker, so no synchronization needed),
    // so a Worker that never needs scratch space leaves no directory on disk.
    private var scratchCreated = false


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun checkpoint() {
        execution.checkpoint()
    }


    override suspend fun <R> runBlockingIo(block: () -> R): R {
        // Offload to the engine's elastic pool via [Execution.blocking]: the fixed engine thread is freed while
        // the I/O blocks, the CountingDispatcher keeps it counted as in-flight (quiescence stays truthful), and
        // engine cancel / migrate interrupt it — so a Worker blocked in a large read no longer holds an engine
        // thread nor stalls the pause / step barrier. One offload per Worker at a time (the Worker awaits it),
        // so the Worker's single-threaded field invariant holds.
        return execution.blocking(block)
    }


    override fun scratchDir(): String {
        if (!scratchCreated) {
            Files.createDirectories(workerScratchDir)
            scratchCreated = true
        }
        return workerScratchDir.toString()
    }


    // Persistent, notation-keyed per-Worker output dir (JobWorkPool.workerOutputDir) — NOT created here: a
    // persisting Worker (Explore) clears + recreates it itself at run start (last-run-wins), and it must survive
    // the run so the result stays downloadable. See JobControl.outputDir.
    override fun outputDir(): String {
        return workerOutputDir.toString()
    }


    override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
        val now = System.nanoTime()
        if (! force && lastProgressNanos != 0L && now - lastProgressNanos < progressThrottleNanos) {
            return
        }
        lastProgressNanos = now

        // The emit address is the Worker node's; the Worker's own stable id (the node's stableId) is what the
        // bridge keys the progress path on, so [location] (always this Worker's own location) is not needed here.
        // NOTE: every emit is retained in the engine's unbounded history, and old progress values are never
        // read back — payload bounding (WorkerBase's teaser-vs-final progress contract) is the mitigation
        // until the engine gains transient (non-retained) emits (see
        // kzen/plans/2026-07-05_logic-engine-improvements.md phase 4); then mark this emit non-retained.
        execution.emit(progressAddress, ExecutionValue.of(value))
    }


    // The Job's declared parameters (its typed input signature) — the scope a Worker's expression compilation
    // exposes by name (compiled by JobLogicCompiler, run-constant).
    override fun parameters(): TupleDefinition {
        return jobParameters.declarations
    }


    // The Job run's argument for [name], read off the root execution's typed inputs tuple (seeded by JobRun),
    // falling back to the declaration's typed default (Script parity — ScriptLogic's binding seed). Null when
    // neither exists (indistinguishable from a bound null — matching TupleValue.find, and acceptable: a bare run's
    // expressions see null).
    override fun parameter(name: String): Any? {
        return jobInputs.find(TupleComponentName(name))
            ?: jobParameters.defaults[TupleComponentName(name)]
    }


    // Contribute a ResultSink Worker's named component to the run's output tuple (harvested by JobRun once the run
    // settles). Last write per component wins, so a re-yield after a live-edit migrate is idempotent.
    override fun yieldResult(component: String, value: Any?) {
        resultCollector.yieldResult(TupleComponentName(component), value)
    }


    override suspend fun host(instructions: ObjectLocation, input: Any?): TupleValue {
        val child = childLogicHost.compile(instructions)

        // Bind the single incoming element to the child's first declared parameter (empty when it declares
        // none) — the same positional convention a Flow Run-Logic vertex uses to pass its upstream message.
        val firstParameter = child.signature().inputs.components.firstOrNull()?.name
        val arguments =
            if (firstParameter != null) {
                TupleValue(listOf(TupleComponentValue(firstParameter, input)))
            }
            else {
                TupleValue.empty
            }

        // Host under the child document's stable id (matching a Script RunStep), so the engine's execution tree
        // and its live-edit migration carry the child by the same identity across a rebuild.
        return execution.host(objectStableMapper.objectStableId(instructions), child, arguments)
    }
}
