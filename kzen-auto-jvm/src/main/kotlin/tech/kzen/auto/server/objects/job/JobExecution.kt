package tech.kzen.auto.server.objects.job

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelClient
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.server.objects.job.channel.DuplexJobChannel
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.worker.WorkerBase
import tech.kzen.auto.server.service.impl.NestedFrameRegistry
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicResourceScope
import tech.kzen.lib.common.exec.logic.model.LogicCommand
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore


/**
 * Runs a [JobDocument]'s graph of concurrent Workers under the kzen-lib Logic/Execution model, generalizing
 * the [tech.kzen.auto.server.objects.report.ReportExecution] concurrent-bridge pattern: the Workers run at
 * full speed on a supervisor-owned [CountingDispatcher] while this `continueOrStart` drives them from the
 * controller-execution thread by polling commands and awaiting quiescence. The same execution instance is
 * reused across pause/step/resume (the live Worker coroutines survive a [LogicResultPaused] return), so
 * partial progress lives in the running Worker instances rather than a StatefulLogicElement of this execution
 * — like [tech.kzen.auto.server.objects.flow.FlowExecution].
 *
 * Best-effort pause/step is cooperative: pause takes effect when each Worker next suspends at a channel
 * boundary or `checkpoint` (a Worker in a tight compute loop with no checkpoint won't park — an M1
 * constraint, acceptable for cooperative Workers).
 *
 * STATE MIGRATION (pause / edit config / continue): the controller re-reads the live (possibly edited)
 * notation each tick and passes it as `graphDefinition`. When that definition changes while paused, this
 * rebuilds the Worker graph from the edit so the new config takes effect ([migrate]) — the live coroutines
 * can't be re-pointed at new config in place. Before tearing the old run down, each Worker's run-scoped state
 * is snapshotted ([WorkerBase.captureMigrationState], while still parked) and keyed by [ObjectStableId]; the
 * matching rebuilt Worker adopts it ([WorkerBase.loadMigrationState]) — so an accumulating Worker keeps its
 * progress and a [tech.kzen.auto.server.objects.job.worker.CsvReaderWorker] continues from its file position
 * (mirrors [tech.kzen.auto.server.objects.script.ScriptExecution]'s identity-continuity). A Worker that doesn't
 * opt in restarts from scratch with the new config; a captured handle whose Worker was REMOVED by the edit is
 * closed (orphan sweep) so it can't leak. In-flight Channel payloads (buffered, or parked mid-send) are likewise
 * carried across the rebuild ([JobChannel.drainBuffered] / [JobChannel.preload]) so the migration neither drops
 * nor double-delivers messages that were between Workers at the cut.
 */
class JobExecution(
    private val documentPath: DocumentPath,
    private val workerLocations: List<ObjectLocation>,
    private val channelLocations: List<ObjectLocation>,
    private val logicTraceHandle: LogicTraceHandle,
    private val logicRunExecutionId: LogicRunExecutionId,
    private val objectStableMapper: ObjectStableMapper,
    private val graphCreator: GraphCreator,
    private val environment: GraphEnvironment,
    private val logicTraceStore: LogicTraceStore,
    private val nestedFrameRegistry: NestedFrameRegistry
):
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(JobExecution::class.java)

        // Re-poll the controller for pause/cancel this often while Workers are still progressing.
        private const val pollIntervalMillis = 50L

        // Once settled-while-running, wait this long before declaring deadlock, so a Worker that is merely
        // mid-completion (its dispatch task done but Job.isCompleted not yet flipped) is recognized as done.
        private const val deadlockGraceMillis = 25L

        // Upper bound a UI -> Worker external round-trip holds the controller monitor (ServerLogicController
        // .request is @Synchronized); a well-behaved serving Worker acks well within this. A blocked / paused
        // Worker makes the request time out rather than stalling status / pause / cancel.
        private const val externalRequestTimeoutMillis = 1000L

        private val parallelism = maxOf(2, Runtime.getRuntime().availableProcessors())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var supervisor: WorkerSupervisor? = null
    private var jobControl: JobControlImpl? = null
    private var logicHost: JobLogicHostImpl? = null
    private var started = false

    // The filtered definition the live Workers were (re)built from, and the live Worker instances keyed by
    // stable id: the change-detection baseline and the loadState donors for the next [migrate].
    private var launchDefinition: GraphDefinition? = null
    private var workersByStableId: Map<ObjectStableId, Worker> = mapOf()

    // The live one-way Channel instances keyed by stable id: a [migrate] drains each one's in-flight payloads
    // before teardown and restores them into the rebuilt channel, so the rebuild doesn't drop messages that
    // were buffered in (or parked mid-send on) a channel at the cut.
    private var streamChannelsByStableId: Map<ObjectStableId, JobChannel> = mapOf()

    // Client endpoints the bridge holds for each `external` duplex Channel, keyed by the Channel's leaf name;
    // `route` forwards inbound ExecutionRequests through these. Populated at launch, closed at teardown.
    private val externalClients = mutableMapOf<String, ChannelClient<Any?, Any?>>()


    //-----------------------------------------------------------------------------------------------------------------
    override fun beforeStart(arguments: TupleValue): Boolean {
        return true
    }


    override fun continueOrStart(
        logicControl: LogicControl,
        resourceScope: LogicResourceScope,
        graphDefinition: GraphDefinition
    ): LogicResult {
        if (logicControl.pollCommand() == LogicCommand.Cancel) {
            return cancelRun()
        }

        val filteredDefinition = graphDefinition.filterTransitive(documentPath)

        if (! started) {
            // Park the fresh Workers at their first checkpoint when the run starts already paused / stepping
            // (a debugger-style start-at-entry), rather than letting them run a brief, nondeterministic free
            // window before the command loop below parks them — that window would leave stray in-flight batches
            // and make a first-tick pause/step state irreproducible.
            buildAndLaunch(
                logicControl, graphDefinition, filteredDefinition, mapOf(), mapOf(),
                initiallyPaused = logicControl.pollCommand() == LogicCommand.Pause)
            logicControl.subscribeRequest(::route)
            started = true
        }
        else if (filteredDefinition.objectDefinitions != launchDefinition!!.objectDefinitions) {
            // Pause / edit config / continue: the notation changed while paused, so rebuild from the edit. A
            // step / pause tick re-parks the fresh Workers at their first checkpoint (initiallyPaused) so the
            // step stays bounded; a full resume lets them run.
            migrate(
                logicControl, graphDefinition, filteredDefinition,
                initiallyPaused = logicControl.pollCommand() == LogicCommand.Pause)
        }

        val supervisor = supervisor!!
        val jobControl = jobControl!!
        val logicHost = logicHost!!

        while (true) {
            when (logicControl.pollCommand()) {
                LogicCommand.Cancel ->
                    return cancelRun()

                LogicCommand.Pause -> {
                    if (logicControl.consumeStepBudget()) {
                        // Step = one global tick: release each parked Worker past exactly one checkpoint (one
                        // wavefront) while staying paused, then await the re-park. Unlike a full resume, the
                        // Workers re-park on their own at their next checkpoint, so this settles after a small,
                        // bounded amount of work — a best-effort step rather than running to completion.
                        // The Job's own budget and each child's budget are now SEPARATE (each child runs on its
                        // own control), so we CONSUME the Job's budget to recognize the step tick, then grant a
                        // fresh boundary to every hosted child so a Step descends one boundary INTO each.
                        logicHost.grantStepToChildren()
                        jobControl.step()
                        supervisor.awaitQuiescent()
                    }
                    else {
                        // Plain pause: park Workers at their next checkpoint, await the quiescent wavefront.
                        jobControl.pause()
                        supervisor.awaitQuiescent()
                    }

                    return terminalResult(supervisor) ?: LogicResultPaused
                }

                LogicCommand.None -> {
                    jobControl.resume()
                    val quiescent = supervisor.awaitQuiescenceOrProgress(pollIntervalMillis)

                    terminalResult(supervisor)?.let {
                        return it
                    }

                    if (quiescent) {
                        if (externalClients.isEmpty()) {
                            // Settled with Workers still live and no external input expected: a Worker is
                            // mid-completion, or a genuine deadlock.
                            Thread.sleep(deadlockGraceMillis)
                            terminalResult(supervisor)?.let {
                                return it
                            }
                            if (supervisor.isQuiescent()) {
                                val failure = supervisor.firstFailure()
                                logger.warn("{} - settled with no progress - {}", documentPath, failure)
                                tearDown()
                                return LogicResultFailed(
                                    failure ?: "Job deadlock: all workers blocked with no progress")
                            }
                        }
                        else {
                            // Externally-driven (service) Job: a Worker idle on an open external channel is
                            // awaiting a UI request, which is indistinguishable from deadlock under the
                            // inFlight==0 heuristic — so deadlock detection is suspended while any external
                            // channel is open. Idle-poll (re-checking Cancel) rather than busy-spin.
                            // (M2 limitation: this also masks a genuine deadlock in a Job that happens to
                            // have an external channel; a precise fix needs per-Worker blocked-on-which-
                            // channel introspection — deferred.)
                            Thread.sleep(pollIntervalMillis)
                        }
                    }
                }
            }
        }
    }


    override fun close(error: Boolean) {
        supervisor?.shutdown()
        logger.info("{} - close - {}", documentPath, error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A terminal result if the run has ended (all Workers done — a recorded failure, else success), or null
    // if Workers are still live. M1 has no harvested output channels yet, so success is the empty tuple.
    private fun terminalResult(supervisor: WorkerSupervisor): LogicResult? {
        if (! supervisor.allTerminated()) {
            return null
        }

        // Surface each Worker's terminal status on its trace ("done" / "failed: <reason>") so the Job panel
        // shows the outcome per Worker instead of leaving "started" forever — the run's only UI feedback.
        for (workerLocation in workerLocations) {
            supervisor.outcome(workerLocation.objectPath.name.value)?.let {
                trace(workerLocation, it)
            }
        }

        val failure = supervisor.firstFailure()
        return if (failure != null) {
            logger.warn("{} - run failed - {}", documentPath, failure)
            LogicResultFailed(failure)
        }
        else {
            LogicResultSuccess(TupleValue.empty)
        }
    }


    // Tear the live run down and rebuild it from the edited definition, migrating each surviving Worker's
    // run-scoped state by stable id. The old coroutines can't be re-pointed at new config in place, so this
    // SNAPSHOTS each Worker's state while it is still parked (so a live handle — e.g. an open file — can be
    // detached before teardown closes it), cancels + joins the old run, then relaunches against the new
    // definition with the snapshots in hand. Reuses the same controller LogicControl / request subscriber.
    private fun migrate(
        logicControl: LogicControl,
        fullDefinition: GraphDefinition,
        filteredDefinition: GraphDefinition,
        initiallyPaused: Boolean
    ) {
        logger.info("{} - rebuilding run from edited definition", documentPath)

        val capturedStates = mutableMapOf<ObjectStableId, Any>()
        for ((stableId, worker) in workersByStableId) {
            (worker as? WorkerBase)?.captureMigrationState()?.let {
                capturedStates[stableId] = it
            }
        }

        // Snapshot each one-way Channel's in-flight payloads (buffered + parked mid-send) while the Workers are
        // still parked and BEFORE teardown cancels the producers (which would drop a parked sender's payload).
        // Captured AFTER the Worker states above so a reader resumed by the drain re-parks at its checkpoint
        // without reading past the position its (already snapshotted) state recorded.
        val carriedChannels = streamChannelsByStableId.mapValues { (_, channel) ->
            channel.drainBuffered()
        }

        tearDown()
        buildAndLaunch(
            logicControl, fullDefinition, filteredDefinition, capturedStates, carriedChannels, initiallyPaused)
    }


    private fun buildAndLaunch(
        logicControl: LogicControl,
        fullDefinition: GraphDefinition,
        filteredDefinition: GraphDefinition,
        capturedStates: Map<ObjectStableId, Any>,
        carriedChannels: Map<ObjectStableId, List<Any?>>,
        initiallyPaused: Boolean
    ) {
        // One shared instance graph for the whole run: the Channel objects are single shared JobChannels,
        // and each Worker's injected endpoint views reference those same instances (via JobChannelCreator).
        val graphInstance = graphCreator.createGraph(filteredDefinition, environment)

        // Resolve the Channel instances: open a bridge client for each `external` duplex Channel so the UI can
        // address it via `route` (the serving Worker then ends only when this client closes at teardown), and
        // index each one-way Channel by stable id — seeding any payloads carried over from the torn-down channel
        // of a [migrate] BEFORE the Workers launch, so the consumer sees that carryover ahead of the live stream.
        externalClients.clear()
        val streamChannels = mutableMapOf<ObjectStableId, JobChannel>()
        for (channelLocation in channelLocations) {
            when (val channel = graphInstance[channelLocation]?.reference) {
                is DuplexJobChannel ->
                    if (channel.external) {
                        externalClients[channelLocation.objectPath.name.value] = channel.newClient()
                    }

                is JobChannel -> {
                    val stableId = objectStableMapper.objectStableId(channelLocation)
                    streamChannels[stableId] = channel
                    carriedChannels[stableId]?.let { channel.preload(it) }
                }
            }
        }

        val supervisor = WorkerSupervisor(parallelism)
        // The host keeps the live (full) graphDefinition so a Run Worker can resolve + run a child Logic from
        // any document; built here so it shares the exact definition the Workers were launched against.
        val logicHost = JobLogicHostImpl(
            fullDefinition, logicRunExecutionId, graphCreator, environment, logicTraceStore,
            nestedFrameRegistry, logicControl)
        val jobControl = JobControlImpl(logicTraceHandle, objectStableMapper, logicHost)

        // A relaunch on a paused / step tick must park the fresh Workers at their first checkpoint before they
        // run free, so a single step-after-edit advances one bounded wavefront (set the phase before launch).
        if (initiallyPaused) {
            jobControl.pause()
        }

        val adoptedStates = mutableSetOf<ObjectStableId>()
        val nextWorkers = mutableMapOf<ObjectStableId, Worker>()
        for (workerLocation in workerLocations) {
            val worker = graphInstance[workerLocation]?.reference as? Worker
            if (worker == null) {
                logger.warn("{} - not a Worker, skipping - {}", documentPath, workerLocation)
                continue
            }

            val stableId = objectStableMapper.objectStableId(workerLocation)
            nextWorkers[stableId] = worker

            // Identity-continuity: a same-stable-id Worker adopts the snapshot the previous instance captured
            // (mirrors ScriptExecution). capturedStates is empty on first launch, so nothing migrates then.
            val captured = capturedStates[stableId]
            if (captured != null && worker is WorkerBase) {
                worker.loadMigrationState(captured)
                adoptedStates.add(stableId)
            }

            supervisor.launch(worker, jobControl, workerLocation.objectPath.name.value)
            trace(workerLocation, "started")
        }

        // Orphan sweep: a captured state whose Worker was removed by the edit is never adopted, so release any
        // detached live handle (e.g. an open file reader) it holds rather than leaking it.
        for ((stableId, state) in capturedStates) {
            if (stableId !in adoptedStates) {
                (state as? AutoCloseable)?.close()
            }
        }

        this.supervisor = supervisor
        this.jobControl = jobControl
        this.logicHost = logicHost
        this.workersByStableId = nextWorkers
        this.streamChannelsByStableId = streamChannels
        this.launchDefinition = filteredDefinition
    }


    private fun cancelRun(): LogicResult {
        tearDown()
        return LogicResultCancelled
    }


    private fun tearDown() {
        externalClients.values.forEach { it.close() }
        jobControl?.cancel()
        // Abort in-flight child Logics before joining: a Worker blocked in a synchronous host.run won't see
        // the coroutine cancel until the child returns, so flip the children to Cancel to unwind them first.
        logicHost?.cancelAll()
        supervisor?.cancelAndJoin()
    }


    // Bridges an inbound UI ExecutionRequest to the serving Worker of the addressed `external` duplex Channel:
    // forwards the request as-is and returns the Worker's reply (the Worker owns the request/reply protocol).
    // Runs on the caller's thread while ServerLogicController holds its monitor, so the round-trip is bounded
    // (externalRequestTimeoutMillis) — a paused / slow Worker yields a timeout failure rather than stalling.
    private fun route(request: ExecutionRequest): ExecutionResult {
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


    private fun trace(objectLocation: ObjectLocation, message: String) {
        logicTraceHandle.set(
            LogicTracePath.ofObjectStableId(objectStableMapper.objectStableId(objectLocation)),
            ExecutionValue.of(message))
    }
}
