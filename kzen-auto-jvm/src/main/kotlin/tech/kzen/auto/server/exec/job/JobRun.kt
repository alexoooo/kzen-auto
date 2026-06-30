package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


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
 * DEFERRED from this first port (tracked parity gaps, mirroring the Flow port): the external duplex request
 * bridge (UI ↔ Worker — and so duplex-channel carryover), nested-logic Workers (RunWorker →
 * [EngineJobControl.logicHost]), deadlock detection, and pause-on-error. A failing Worker fails the run
 * (structured concurrency cancels its siblings) rather than the old `SupervisorJob` fail-at-end with per-Worker
 * outcome reporting. The old `server.objects.job.{JobExecution,JobDocument}` stay in place (reference + still
 * driven by the old tests); removal is deferred to the post-all-flavours cleanup.
 */
class JobRun(
    private val execution: Execution,
    private val filteredDefinition: GraphDefinition,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val objectStableMapper: ObjectStableMapper,
    private val graphEnvironment: GraphEnvironment
) {
    suspend fun run(): TupleValue {
        // One shared instance graph for the whole run: the Channel objects are single shared instances and each
        // Worker's injected endpoint views reference them (via JobChannelCreator) — exactly as the old
        // buildAndLaunch built it.
        val graphInstance = GraphCreator.createGraph(filteredDefinition, graphEnvironment)

        // Index the one-way stream Channels by stable id (duplex channels — the external bridge — are a deferred
        // parity gap, so they carry nothing here). The deterministic synthesized identity means a rebuilt run
        // resolves the SAME stable ids, so channel carryover lines up across a migrate.
        val streamChannels = LinkedHashMap<ObjectStableId, JobChannel>()
        for (channelLocation in channelLocations) {
            val channel = graphInstance[channelLocation]?.reference as? JobChannel
                ?: continue
            streamChannels[objectStableMapper.objectStableId(channelLocation)] = channel
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

        val workers = workerLocations.mapNotNull { location ->
            val worker = graphInstance[location]?.reference as? Worker
                ?: return@mapNotNull null
            location to worker
        }

        // Launch every Worker concurrently as its own confined node; the run settles when all of them do.
        coroutineScope {
            workers
                .map { (location, worker) ->
                    async {
                        execution.host(objectStableMapper.objectStableId(location), WorkerLogic(worker))
                    }
                }
                .awaitAll()
        }

        return TupleValue.empty
    }
}
