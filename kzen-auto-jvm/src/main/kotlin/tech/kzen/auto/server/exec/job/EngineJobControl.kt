package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.LentElement
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.engine.resource.Hold
import tech.kzen.lib.common.exec.engine.resource.Holder
import tech.kzen.lib.common.exec.engine.resource.ResourceOwners
import tech.kzen.lib.common.exec.engine.resource.ResourceRegistry
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit


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
 *
 * [draining] is the upstream-first quiescence rule of the content streaming spike (CS3, design §6.7): while an
 * entry scope upstream of this Worker (or this Worker itself, if it is the scope) is inside an entry,
 * [checkpoint] returns without parking, so a consumer keeps draining and the scope can reach its boundary
 * against a bounded channel, and the scope never parks inside an entry whatever its body checkpoints (a
 * reader's per-batch checkpoint); a pause then lands with the scope between entries. A breakpoint on such a
 * Worker is likewise reached only once the scope is at its boundary. Running free the gate costs nothing,
 * since an ungated checkpoint would not park either.
 */
class EngineJobControl(
    private val execution: Execution,
    private val childLogicHost: JobChildLogicHost,
    private val objectStableMapper: ObjectStableMapper,
    private val workerScratchDir: Path,
    private val workerOutputDir: Path,
    private val jobInputs: DataBindings,
    private val jobParameters: JobParameters,
    private val jobResults: BindingSchema,
    private val inputPayloadType: TypeMetadata?,
    private val inputContract: DataContract?,
    private val outputContract: DataContract?,
    private val resultCollector: JobResultCollector,
    /** The Job root's [Execution]: the frame that holds a yielded Result until the Job settles (E9, plan §3.3). */
    private val jobExecution: Execution,
    private val workerLocation: ObjectLocation,
    private val draining: () -> Boolean,
    /** When the run started ([System.nanoTime]), for the elapsed time stamped on progress. */
    private val runStartedNanos: Long = System.nanoTime()
): JobControl, RunResourceControl {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Reserved emit address tagging a Worker's live-progress payload, distinct from any other value the
        // Worker node emits; the controller's trace bridge recognizes it and routes to the Worker's progress
        // trace path. A stable id can never collide (it is an ObjectLocation string).
        const val workerProgressAddressMarker = "\$job-progress"

        // Minimum spacing between (non-forced) progress emits per Worker, mirroring JobControlImpl — a forced
        // write (the final end-of-stream value) always lands.
        private const val progressThrottleNanos = 200_000_000L  // 200 ms

        /** Test seam: false makes every checkpoint park regardless of an upstream scope (spike CS3 verification). */
        @Volatile
        internal var drainingEnabled = true


        internal fun normalizeArguments(
            instructions: ObjectLocation,
            signature: BindingSchema,
            arguments: DataBindings
        ): DataBindings {
            val supplied = arguments.entries().mapNotNull { (definition, state) ->
                val target = signature.find(definition.name)
                require(target != null) {
                    "Unknown child argument '${definition.name}' for $instructions"
                }
                val bound = state as? BindingState.Bound ?: return@mapNotNull null
                definition.name to JobDataValues.lift(
                    JobDataValues.boundary(bound.value),
                    target.contract)
            }
            return DataBindings.bind(signature, supplied)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val progressAddress = Address.of(workerProgressAddressMarker)

    // The holder every callback hold of this Worker is named by: its notation location, so a live-edit
    // replacement instance is the same holder
    private val workerHolder = Holder(workerLocation.asString())

    /** The run's resource registry (E9): shared by every Worker of the run, surviving a live edit. */
    override val resources: ResourceRegistry
        get() = execution.resources

    // This Worker runs single-threaded on its own node coroutine, so a plain field suffices (no concurrent
    // publishProgress for one Worker).
    private var lastProgressNanos = 0L

    // Created lazily on the first scratchDir() call (single-threaded per Worker, so no synchronization needed),
    // so a Worker that never needs scratch space leaves no directory on disk.
    private var scratchCreated = false


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun checkpoint() {
        if (drainingEnabled && draining()) {
            return
        }
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
        if (!force && lastProgressNanos != 0L && now - lastProgressNanos < progressThrottleNanos) {
            return
        }
        // This Worker's live callback holds ride its ordinary progress (E9 item 5), aggregated only, and the
        // run's elapsed time stamps it, so the client can work out a rate from two of them
        val holds = resources.holdsOf(workerHolder)
        @Suppress("NAME_SHADOWING")
        val value = LinkedHashMap(value).also {
            if (holds != 0) {
                it[JobConventions.progressHoldsKey] = holds
            }
            it[JobConventions.progressElapsedKey] = TimeUnit.NANOSECONDS.toMillis(now - runStartedNanos)
        }
        lastProgressNanos = now

        // The emit address is the Worker node's; the Worker's own stable id (the node's stableId) is what the
        // bridge keys the progress path on, so [location] (always this Worker's own location) is not needed here.
        // Transient: only the latest progress is read, from the live view, so none of it is kept in the history.
        execution.emit(progressAddress, ExecutionValue.of(value), retain = false)
    }


    // The Job's declared parameters (its typed input signature) — the scope a Worker's expression compilation
    // exposes by name (compiled by JobLogicCompiler, run-constant).
    override fun parameters(): BindingSchema {
        return jobParameters.declarations
    }


    // The Job's declared results (its typed output signature, from the document's `results` map) — a ResultSink
    // reads its component's declared type off it (nullability decides the empty-stream rule).
    override fun results(): BindingSchema {
        return jobResults
    }


    // This Worker's inferred INPUT payload type, threaded from the static payload-type walk by JobRun (null =
    // untyped/flat lane) — the receiver scope for runtime expression compiles, matching the editor's display.
    override fun payloadType(): TypeMetadata? {
        return inputPayloadType
    }


    override fun inputContract(): DataContract? {
        return inputContract
    }


    override fun outputContract(): DataContract? {
        return outputContract
    }


    // The Job run's argument for [name], read off the root execution's typed inputs (seeded by JobRun),
    // falling back to the declaration's typed default (Script parity — ScriptLogic's binding seed). Null when
    // neither exists (indistinguishable from a bound null, and acceptable: a bare run's
    // expressions see null).
    override fun parameter(name: String): Any? {
        return when (val state = jobInputs[BindingName(name)]) {
            BindingState.Unbound -> null
            is BindingState.Bound -> JobDataValues.boundary(state.value)
        }
    }


    // A Worker's own hold past its callback (an accumulator): a hold of this Worker's frame, released by the
    // Worker or at the latest when its node settles. A lent element is refused by name (borrowed elements §3.3):
    // its source waits for the release before it advances, so a hold that outlives the callback would hang the
    // run rather than keep the element.
    override fun retain(value: DataValue): Hold {
        lentOwner(value.owners)?.let { lent ->
            throw IllegalStateException(
                "$workerLocation cannot keep ${lent.lentName()}: it is borrowed from ${lent.lender()} and is " +
                        "only valid until the source advances. Snapshot it, read it, or write it.")
        }
        return execution.hold(value)?.hold ?: Hold.none
    }


    // The per-callback hold is a plain registry hold under this Worker's name: the per-element path takes no
    // engine lock, and the run's progress counts it (publishProgress)
    override fun holdForCallback(value: DataValue): Hold {
        return resources.hold(value, workerHolder)?.hold ?: Hold.none
    }


    private fun lentOwner(owners: ResourceOwners?): LentElement? =
        owners?.entries()?.firstNotNullOfOrNull { it.native as? LentElement }


    // Contribute a ResultSink Worker's named binding to the run's output (harvested by JobRun once the run
    // settles). Last write per component wins, so a re-yield after a live-edit migrate is idempotent.
    override fun yieldResult(component: String, value: DataValue) {
        // A Result over a resource is the live value: the Job's root frame holds it from here until the Job
        // settles, so a hosting frame reads it open (plan §3.3); a lent element is named, its source would wait
        lentOwner(value.owners)?.let { lent ->
            throw IllegalStateException(
                "Result '$component' of $workerLocation cannot keep ${lent.lentName()}: it is borrowed from " +
                        "${lent.lender()} and is only valid until the source advances. " +
                        "Snapshot it, read it, or write it.")
        }
        val held = jobExecution.hold(value)
        resultCollector.yieldResult(BindingName(component), held?.value ?: value)
    }


    override fun log(location: ObjectLocation, value: Map<String, Any?>) {
        execution.log(MapExecutionValue(value.mapValues { logValue(it.value) }))
    }


    private fun logValue(value: Any?): ExecutionValue {
        return when (value) {
            is ExecutionValue -> value
            is Map<*, *> -> MapExecutionValue(value.entries.associate {
                require(it.key is String) { "Job log map keys must be strings" }
                it.key as String to logValue(it.value)
            })
            is List<*> -> ListExecutionValue(value.map(::logValue))
            else -> ExecutionValue.of(value)
        }
    }


    override suspend fun host(instructions: ObjectLocation, input: Any?): DataBindings {
        val child = childLogicHost.compile(instructions)
        val signature = child.signature()

        // Bind the single incoming element to the child's first declared parameter (empty when it declares
        // none) — the same positional convention a Flow Run-Logic vertex uses to pass its upstream message.
        val firstParameter = signature.inputs.definitions.firstOrNull()
        val arguments =
            if (firstParameter != null) {
                DataBindings.bind(
                    signature.inputs,
                    firstParameter.name to JobDataValues.lift(input, firstParameter.contract))
            }
            else {
                DataBindings.bind(BindingSchema.empty)
            }

        // Host under the child document's stable id (matching a Script RunStep), so the engine's execution tree
        // and its live-edit migration carry the child by the same identity across a rebuild.
        return hostCompiled(instructions, child, arguments)
    }


    override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
        val child = childLogicHost.compile(instructions)
        val signature = child.signature()
        val ordered = normalizeArguments(instructions, signature.inputs, arguments)
        return hostCompiled(instructions, child, ordered)
    }


    private suspend fun hostCompiled(
        instructions: ObjectLocation,
        child: Logic,
        arguments: DataBindings
    ): DataBindings {
        return execution.host(
            stableId = objectStableMapper.objectStableId(instructions),
            child = child,
            inputs = arguments)
    }
}
