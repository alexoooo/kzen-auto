package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelClient
import tech.kzen.auto.server.objects.job.JobValidator
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.LogicParameterTrace
import tech.kzen.auto.server.objects.job.channel.DuplexJobChannel
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.exec.engine.restoredAs
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import java.util.concurrent.atomic.AtomicInteger
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.time.Duration.Companion.milliseconds


/**
 * One run of a [JobLogic]'s Worker graph: builds the one shared instance graph (Workers plus the Channels
 * [tech.kzen.auto.server.objects.job.JobChannelCreator] wires between them), hosts each Worker as its own
 * confined engine node ([Execution.host]) under structured concurrency, and completes when every Worker
 * settles. Engine semantics (checkpoints, quiescent wavefront, migrate barrier) are canonical in kzen-lib's
 * logic-spec; the Job paradigm is canonical in kzen-auto's docs/architecture.md. What JobRun itself owns:
 *
 * - Signature: run arguments arrive as the root [execution]'s [Execution.inputs] tuple, read by name via
 *   [EngineJobControl.parameter]; ResultSink yields are gathered per run by [JobResultCollector] into the
 *   returned [TupleValue]. Seeding / harvest is generic — no Worker-type knowledge.
 * - Payload types: [JobValidator.validate]'s static walk (cache-shared with the editor's detached validation)
 *   threads each Worker's inferred input payload type into its [EngineJobControl].
 * - Channel carry-over across a live edit (logic-spec §5): Channels are shared instances, not nodes, so the
 *   Job's root node owns their in-flight payloads — [Execution.onCapture] drains every one-way [JobChannel]
 *   at the quiescent barrier keyed by [stable id][ObjectStableId], and the rebuilt run [JobChannel.preload]s
 *   each fresh channel before any Worker launches, so no row is dropped or replayed across the cut.
 *   (Per-Worker run-scoped state migrates at the Worker's own node instead — see [WorkerLogic].)
 * - External duplex bridge (logic-spec §4): each `external` duplex Channel gets a UI-bridge client opened at
 *   every (re)launch and closed at teardown; the root node's [Execution.onRequest] router forwards inbound
 *   requests by `channel` name to the serving Worker (see [route]).
 * - Deadlock: [JobDeadlockMonitor] polls this run's stream channels off the engine dispatcher and fails the
 *   run through an exceptionally-completed signal; the open external bridge is its suppression signal.
 */
class JobRun(
    private val execution: Execution,
    private val jobLocation: ObjectLocation,
    private val filteredDefinition: GraphDefinition,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val jobParameters: JobParameters,
    private val jobResults: TupleDefinition,
    private val graphNotation: GraphNotation,
    private val graphDefinition: GraphDefinition,
    private val services: LogicCompilerServices
) {
    private val objectStableMapper get() = services.objectStableMapper
    private val graphEnvironment get() = services.graphEnvironment
    private val jobWorkPool get() = services.jobWorkPool

    // Migrate-stable: keys each Worker's scratch dir so a rebuilt run resolves the same paths across a live edit.
    private val runId get() = services.runExecutionId.logicRunId


    suspend fun run(): TupleValue {
        // Surface each declared parameter's resolved value (bound argument falling back to the declared default)
        // at the parameter's own address, once per (re)launch — the signature editor shows it beside the declared
        // default. Emitted on the Job's ROOT node: parameters belong to the document, not any Worker.
        LogicParameterTrace.emitAll(execution, jobParameters.bindings)

        // Shared across every Worker of this run: compiles + caches the child Logics that nested-Logic Workers
        // (RunWorker) host. Built from the FULL graph (not [filteredDefinition]), since a child is a different
        // document. Each Worker still hosts under its own node — this holds only the reusable compiled Logics.
        val childLogicHost = JobChildLogicHost(graphNotation, graphDefinition, services)

        // Parameter seeding + result harvest (JobSignatureCapability's runtime half): the root [execution]'s typed
        // [Execution.inputs] tuple carries the Job's bound run arguments (a Worker reads a declared parameter by
        // name via JobControl.parameter), and this collector gathers what the ResultSinkWorkers yield into the
        // tuple returned as the run's result. Owned per run — a migrate rebuilds it empty, so a carried sink
        // re-yields at its onComplete (yield is last-write-wins).
        val resultCollector = JobResultCollector()

        // One shared instance graph for the whole run: the Channel objects are single shared instances and each
        // Worker's injected endpoint views reference them (via JobChannelCreator) — exactly as the old
        // buildAndLaunch built it.
        val graphInstance = GraphCreator.createGraph(filteredDefinition, graphEnvironment)

        // Resolve the Channel instances: index each one-way stream Channel by stable id (for migration carryover),
        // and open a UI-bridge client for each `external` duplex Channel (a Worker's UI-facing `serve` port, e.g.
        // the Preview's slice query) so [route] can address its serving Worker by name. The deterministic
        // synthesized identity means a rebuilt run resolves the SAME stable ids / names, so a migrate lines up.
        val streamChannels = LinkedHashMap<ObjectStableId, JobChannel>()
        val externalClients = LinkedHashMap<String, ChannelClient<Any?, Any?>>()
        for (channelLocation in channelLocations) {
            when (val channel = graphInstance[channelLocation]?.reference) {
                is DuplexJobChannel ->
                    if (channel.external) {
                        externalClients[channelLocation.objectPath.name.value] = channel.newClient()
                    }

                is JobChannel ->
                    streamChannels[objectStableMapper.objectStableId(channelLocation)] = channel
            }
        }

        // Restore: seed each channel with the in-flight payloads its predecessor (same stable id) was carrying at
        // the edit, BEFORE any Worker launches — so the consumer drains the carryover ahead of the live stream.
        val carriedChannels = execution.restoredAs<Map<ObjectStableId, List<Any?>>>()
        if (carriedChannels != null) {
            for ((stableId, channel) in streamChannels) {
                carriedChannels[stableId]?.let { channel.preload(it) }
            }
        }

        // Capture: at the migration barrier (run quiescent, BEFORE teardown), snapshot each channel's in-flight
        // payloads so the rebuilt run can re-seed them. Safe to drain here because the engine pauses the run
        // before invoking this, so a Worker an in-progress drain unparks re-parks at its next checkpoint rather
        // than producing more (and drainBuffered dedups a sender that completes mid-drain — see JobChannel).
        execution.onCapture {
            streamChannels.mapValues { (_, channel) -> channel.drainBuffered() }
        }

        // Sweep this run's whole scratch tree (every file-backed Worker's on-disk store) when the run settles —
        // a run-root belt-and-suspenders over each stateful Worker's own close-then-delete onClose, keyed on the
        // migrate-stable run id (a boot sweep covers a hard kill). Auto = fires on success / failure / cancel.
        // Anonymous and frame-local: there is no handle to hand anyone and nothing ever looks this up, so it
        // carries no name — the run id it closes over is the only identity the sweep needs.
        execution.onSettle(SettleDisposalPolicy.Auto) {
            jobWorkPool.deleteRun(runId)
        }

        // External duplex bridge: route an inbound UI request (addressed by `channel` name, e.g. the JS pulling a
        // larger Preview slice) to the serving Worker of that `external` duplex Channel. Registered on the Job's
        // ROOT node, which is the frame the JS addresses (LogicRunInfo.frame = root). Also tells the engine the run
        // is externally serviceable, so a Worker idle on an open serve port is not mistaken for a deadlock.
        if (externalClients.isNotEmpty()) {
            execution.onRequest { request -> route(request, externalClients) }
        }

        val workers = workerLocations.mapNotNull { location ->
            val worker = graphInstance[location]?.reference as? Worker
                ?: return@mapNotNull null
            location to worker
        }

        // The static payload-type walk (shared with the editor's detached JobValidator through the cache — a
        // hit reuses the editor's entry, a miss computes on THIS run's instances): each Worker's inferred
        // INPUT payload type is threaded into its control, so runtime expression compiles use the same
        // receiver the walk (and the editor's cards) derived.
        val jobValidation = services.jobValidationCache.jobValidation(
            jobLocation.documentPath, graphDefinition
        ) {
            JobValidator.validate(
                jobLocation.documentPath, graphDefinition.graphStructure, graphInstance)
        }
        val upstreamByDownstream = JobChannelDerivation
            .derive(graphDefinition.graphStructure, jobLocation.documentPath)
            .connections
            .associate { it.downstreamWorker.objectPath to it.upstreamWorker.objectPath }

        // Channel-aware deadlock detection: fail the run if every non-terminal Worker becomes blocked on a channel
        // with no way to progress. [activeWorkers] is the live non-terminal count (each Worker decrements it as it
        // settles); the monitor polls the stream channels' blocked-endpoint counts against it and, on a sustained
        // all-blocked verdict, completes the signal exceptionally. Suppressed while an external serve channel is
        // open (see [JobDeadlockMonitor]).
        val activeWorkers = AtomicInteger(workers.size)
        val deadlockSignal = CompletableDeferred<Nothing>()
        val deadlockMonitor = JobDeadlockMonitor(
            streamChannels.values, activeWorkers, externalClients.isNotEmpty()
        ) {
            deadlockSignal.completeExceptionally(
                LogicFailure("Job deadlock: all workers blocked on channels with no progress"))
        }
        deadlockMonitor.start()

        // Launch every Worker concurrently as its own confined node; the run settles when all of them do. The
        // external bridge clients are closed at teardown (run end, cancel, or migrate) so the serving Workers'
        // serve streams end and the rebuilt run re-opens fresh clients.
        try {
            coroutineScope {
                // Fails the scope (and so the run) the instant the monitor declares a deadlock; cancelled once the
                // Workers settle on their own. Awaiting a deferred suspends without occupying the dispatcher, so it
                // never perturbs the engine's quiescence.
                val deadlockGuard = launch { deadlockSignal.await() }

                workers
                    .map { (location, worker) ->
                        val workerStableId = objectStableMapper.objectStableId(location)
                        // Resolved (not yet created) here so the Worker's EngineJobControl.scratchDir() can
                        // materialize it lazily on first use — a Worker that needs no scratch space leaves none.
                        val workerScratchDir = jobWorkPool.workerScratchDir(runId, workerStableId)
                        // Persistent, notation-keyed output dir (survives run-settle): a persisting sink (Explore)
                        // clears + rewrites it so its result stays browsable / downloadable after the run ends.
                        val workerOutputDir = jobWorkPool.workerOutputDir(location)
                        // The Worker's inferred INPUT payload type: its inferred upstream's output type per
                        // the walk (null = untyped/flat — the expression receiver falls back to nullable Any).
                        val inputPayloadType = upstreamByDownstream[location.objectPath]
                            ?.let { jobValidation.workerValidations[it]?.typeMetadata }
                        async {
                            try {
                                execution.host(
                                    workerStableId,
                                    WorkerLogic(
                                        worker, childLogicHost, objectStableMapper,
                                        workerScratchDir, workerOutputDir,
                                        execution.inputs, jobParameters, jobResults,
                                        inputPayloadType, resultCollector),
                                    // These frames are live SIMULTANEOUSLY, which is the one shape the engine's
                                    // ambient-context model is not specified for (logic-spec §6): two Workers
                                    // binding one exported key would collapse onto a single slot on this Job
                                    // frame, where the second bind displaces the first, claims its disposal and
                                    // runs the closer while that Worker is still using what it just closed. The
                                    // engine lock makes that safe, not meaningful — the winner is whichever
                                    // coroutine arrived second. The barrier removes the shared slot instead of
                                    // policing it, so there is no order for the outcome to depend on. Reads are
                                    // unaffected: a Worker still inherits everything this frame bound.
                                    contextBarrier = true)
                            }
                            finally {
                                activeWorkers.decrementAndGet()
                            }
                        }
                    }
                    .awaitAll()

                deadlockGuard.cancel()
            }
        }
        finally {
            deadlockMonitor.close()
            externalClients.values.forEach { it.close() }
        }

        return resultCollector.toTupleValue()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Bridge an inbound UI [ExecutionRequest] to the serving Worker of the addressed `external` duplex Channel:
    // forward it as-is over the channel's client and return the Worker's reply (the Worker owns the request/reply
    // protocol via its serve loop). Runs on the controller thread while it holds its monitor, so the round-trip is
    // bounded ([externalRequestTimeoutMillis]) — a paused / slow Worker yields a timeout rather than stalling.
    private fun route(
        request: ExecutionRequest,
        externalClients: Map<String, ChannelClient<Any?, Any?>>
    ): ExecutionResult {
        val channelName = request.getSingle(JobConventions.channelParameter)
            ?: return ExecutionResult.failure("Missing '${JobConventions.channelParameter}' parameter")

        val client = externalClients[channelName]
            ?: return ExecutionResult.failure("Not an open external channel: $channelName")

        return runBlocking {
            val reply = withTimeoutOrNull(externalRequestTimeoutMillis) {
                client.request(request)
            }
            reply as? ExecutionResult
                ?: ExecutionResult.failure("External channel request timed out: $channelName")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Upper bound a UI -> Worker external round-trip blocks the controller monitor (request is @Synchronized);
        // a well-behaved serving Worker replies well within this. A blocked / paused Worker makes the request time
        // out rather than stalling status / pause / cancel. KNOWN BOUNDED SEAM: this is a `runBlocking` on the
        // controller thread; moving the wait off-monitor is only cheap once the controller is per-run (engine
        // plan E6, deferred), so the timeout-capped wait stands rather than redesigning the duplex bridge.
        private val externalRequestTimeoutMillis = 1000.milliseconds
    }
}
