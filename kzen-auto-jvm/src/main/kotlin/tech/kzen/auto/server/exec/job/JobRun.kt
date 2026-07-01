package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelClient
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.channel.DuplexJobChannel
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * One run of a [JobLogic]'s Worker graph on the new engine: the coroutine-shaped successor to
 * [tech.kzen.auto.server.objects.job.JobExecution]'s re-entrant `continueOrStart` + `WorkerSupervisor`. It
 * builds the one shared instance graph (the Workers plus the Channels [tech.kzen.auto.server.objects.job.JobChannelCreator]
 * wires between them), then hosts each Worker as its OWN confined engine node ([Execution.host]) launched
 * concurrently with structured concurrency. The run completes when every Worker settles.
 *
 * KEY COLLAPSE: each Worker checks pause / step / cancel at its own [Execution.checkpoint] (via [EngineJobControl]),
 * and the engine's `CountingDispatcher` brings the Workers to a quiescent wavefront — so the old supervisor poll
 * loop, the shared `JobControlImpl` phase + release-signal, and the manual await / deadlock-grace machinery all
 * collapse into `coroutineScope { … host … }` + the engine. A Worker suspended on a channel `receive` / `send`
 * (or parked at a checkpoint) leaves the dispatcher, so `inFlight == 0` is exactly the wavefront the engine
 * pauses / steps on. Bulk data flows Worker-to-Worker through the channels (engine-owned buffers), never the
 * trace fold; only per-Worker progress is emitted (observability-rate, throttled — see [EngineJobControl]).
 *
 * LIVE-EDIT MIGRATION (logic-spec §5): each per-run [JobRun] is rebuilt from scratch by the engine's migrate
 * barrier (a fresh instance graph, fresh Channels, fresh Worker nodes). Two kinds of state survive the rebuild,
 * carried by [stable id][ObjectStableId]:
 * - Per-Worker run-scoped state — adopted at the Worker's own node (see [WorkerLogic]).
 * - In-flight Channel payloads — owned here, since the Channels are not nodes but shared instances this run
 *   builds. The Job's ROOT node carries them: [Execution.onCapture] drains every one-way [JobChannel]
 *   ([JobChannel.drainBuffered]) at the quiescent barrier BEFORE teardown (buffered + parked-mid-send, in
 *   delivery order), keyed by the channel's stable id; the rebuilt run [Execution.restored]s that map and
 *   [JobChannel.preload]s each fresh channel before any Worker launches — so the consumer sees the exact stream
 *   it would have without the edit, neither dropping nor replaying a row across the cut (mirrors the old
 *   [tech.kzen.auto.server.objects.job.JobExecution.migrate]).
 *
 * EXTERNAL DUPLEX BRIDGE (logic-spec §4): each `external` duplex Channel (a Worker's UI-facing `serve` port,
 * e.g. the Preview's slice query) gets a UI-bridge client opened here at launch; the Job's ROOT node registers an
 * [Execution.onRequest] router that forwards an inbound request (addressed by `channel` name) to that client's
 * serving Worker and returns its reply (see [route]). Re-opened on every (re)launch, so it survives a migrate;
 * closed at teardown so the serving Worker's serve stream ends. Mirrors the old
 * `JobExecution.route` / `externalClients`.
 *
 * DEFERRED (remaining tracked parity gaps): nested-logic Workers (RunWorker → [EngineJobControl.logicHost]) and
 * the Job pause-on-error tied to them. Deadlock detection (a Job whose Workers all block on channels with no
 * progress) is the engine's stall guard; the external bridge above is its suppression signal (an externally
 * serviceable run is not a deadlock). A failing Worker currently fails the run (structured concurrency cancels
 * its siblings) rather than the old `SupervisorJob` fail-at-end with per-Worker outcome reporting.
 */
class JobRun(
    private val execution: Execution,
    private val filteredDefinition: GraphDefinition,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val graphNotation: GraphNotation,
    private val graphDefinition: GraphDefinition,
    private val services: LogicCompilerServices
) {
    private val objectStableMapper get() = services.objectStableMapper
    private val graphEnvironment get() = services.graphEnvironment


    suspend fun run(): TupleValue {
        // Shared across every Worker of this run: compiles + caches the child Logics that nested-Logic Workers
        // (RunWorker) host. Built from the FULL graph (not [filteredDefinition]), since a child is a different
        // document. Each Worker still hosts under its own node — this holds only the reusable compiled Logics.
        val childLogicHost = JobChildLogicHost(graphNotation, graphDefinition, services)

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
        @Suppress("UNCHECKED_CAST")
        val carriedChannels = execution.restored as? Map<ObjectStableId, List<Any?>>
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

        // Launch every Worker concurrently as its own confined node; the run settles when all of them do. The
        // external bridge clients are closed at teardown (run end, cancel, or migrate) so the serving Workers'
        // serve streams end and the rebuilt run re-opens fresh clients.
        try {
            coroutineScope {
                workers
                    .map { (location, worker) ->
                        async {
                            execution.host(
                                objectStableMapper.objectStableId(location),
                                WorkerLogic(worker, childLogicHost, objectStableMapper))
                        }
                    }
                    .awaitAll()
            }
        }
        finally {
            externalClients.values.forEach { it.close() }
        }

        return TupleValue.empty
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
        // out rather than stalling status / pause / cancel.
        private const val externalRequestTimeoutMillis = 1000L
    }
}
