package tech.kzen.auto.client.service.logic

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.w3c.dom.EventSource
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.HttpStatusException
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.common.paradigm.logic.LogicControlReply
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import kotlin.js.Date
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import kotlin.time.Duration.Companion.milliseconds


class ClientLogicGlobal(
    private val restClient: ClientRestApi,
    private val objectStableMapper: ObjectStableMapper
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // The push-vs-poll cadences, the SSE probe/staleness windows and the publish throttle are all
        // specified in architecture.md § 3 (REST API surface) — the canonical home for why each is what it is.
        private const val debounceMillis = 1_500
        private const val pushDebounceMillis = 10_000
        private const val statusPublishThrottleMillis = 1_000
        private const val sseProbeMillis = 3_000
        private const val sseStaleMillis = 45_000

        // "Slow motion" auto-step pacing: the visible dwell between auto-issued steps, and the cadence /
        // cap used to wait for each step to settle back to Paused before issuing the next.
        private const val slowPacingMillis = 750

        // How often awaitStepSettled re-checks for settle: BOTH the push-healthy local-state re-check
        // granularity AND the poll floor when push is absent. Well under the dwell so settle detection stays
        // imperceptible, while a no-push deployment polls status at ~5/s. When push is healthy this loop
        // issues no requests at all (see awaitStepSettled), so this only bounds detection latency there.
        private const val slowSettlePollMillis = 200
        private const val slowSettleMaxMillis = 30_000

        // Settle before re-reading status after a control verb landed: the server submits the verb to its
        // executor and answers immediately, so an instant read would race the state change it asked for.
        private const val controlSettleMillis = 10

        // Shared by the two ways to start a run (plain / slow-motion) and the two trace clears (this document /
        // all documents), so a failure reads the same wherever it was triggered from.
        private const val startLabel = "Unable to start"
        private const val clearTraceLabel = "Unable to clear the trace"

        // Shared by a move the SERVER refuses and one the client rules out before asking, so a refused move
        // reads the same however it was caught.
        private const val moveRejectedLabel = "Can't move to this step"
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onLogic(clientLogicState: ClientLogicState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var clientLogicState: ClientLogicState = ClientLogicState()


    fun observe(observer: Observer) {
        observers.add(observer)
        observer.onLogic(clientLogicState)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun publish() {
        for (observer in observers) {
            observer.onLogic(clientLogicState)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun init() {
        lookupStatus()

        val running = clientLogicState.isExecuting()

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.None)

        publish()

        // Push updates only while this tab is in front (see connectEventSourceIfNeeded); react to it
        // changing for the life of the page.
        document.addEventListener("visibilitychange", {
            onVisibilityChanged()
        })

        if (running) {
            scheduleRefresh()
        }
    }


    // Returns whether this status changed the run's STRUCTURE — see publishStatus, the only consumer that
    // cares. Callers that must publish unconditionally (every control verb) ignore it.
    private suspend fun lookupStatus(): Boolean {
        return applyStatus(restClient.logicStatus())
    }


    // The single point where a LogicStatus — however it arrived, polled or pushed — becomes client state.
    private fun applyStatus(logicStatus: LogicStatus): Boolean {
        val previousStructureVersion = clientLogicState.structureVersion()

        clientLogicState = clientLogicState.copy(
            logicStatus = logicStatus)

        return clientLogicState.structureVersion() != previousStructureVersion
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Fan-out throttle for statuses arriving from the TRANSPORT (pushed or polled — one rule, either courier);
    // structure-changed publishes at once, sequence-only defers. Rationale: architecture.md § 3.
    // Control verbs deliberately bypass it: they are one per user action and must land immediately.
    private val publishStatusThrottle: FunctionWithDebounce = lodash.throttle({
        publish()
    }, statusPublishThrottleMillis)


    private fun publishStatus(structureChanged: Boolean) {
        if (structureChanged) {
            publishStatusThrottle.cancel()
            publish()
            return
        }

        publishStatusThrottle.apply()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Push transport, with the poll loop armed as an adaptive fallback (see scheduleRefresh). The push /
    // poll contract and the two silent-failure modes these flags separate: architecture.md § 3.
    private var eventSource: EventSource? = null

    // Delivery-proven, never connection-proven. Also lets the slow-motion settle wait skip its network polling.
    private var sseHealthy: Boolean = false
    private var lastSseMessageMillis: Double = 0.0

    // Latched on opened-but-mute (a buffering intermediary): push is given up on for this page's life.
    private var sseUnavailable: Boolean = false

    // Whether the CURRENT connection reached open — opened-but-mute latches, never-opened is left to
    // EventSource's own retry.
    private var sseOpened: Boolean = false


    // Page Visibility isn't in the Kotlin DOM externals, hence the dynamic read. Tested against "hidden"
    // rather than for "visible" so that anything unexpected (an absent API, "prerender") counts as visible:
    // the failure mode of a wrong TRUE is one extra connection, of a wrong FALSE is a UI that never updates.
    private fun isDocumentVisible(): Boolean {
        return document.asDynamic().visibilityState != "hidden"
    }


    private fun connectEventSourceIfNeeded() {
        if (eventSource != null || sseUnavailable) {
            return
        }

        // Only the visible tab holds a stream open — the connection-budget mitigation in architecture.md § 3.
        if (!isDocumentVisible()) {
            return
        }

        // Nothing to watch: no run is executing. Matches scheduleRefresh's gate.
        if (!clientLogicState.isExecuting()) {
            return
        }

        val source = EventSource(restClient.logicEventsUrl())
        sseOpened = false

        // Deliberately does NOT mark the stream healthy — only an arriving message proves delivery.
        source.onopen = {
            sseOpened = true
        }

        source.onmessage = { messageEvent ->
            noteSseMessage()
            val data = messageEvent.data as? String
            if (data != null) {
                publishStatus(applyStatus(restClient.parseLogicStatusText(data)))

                // Lets scheduleRefresh see the new state and close the stream once the run is no longer
                // executing (the terminal push is the last thing this stream owes us). Reads clientLogicState,
                // which applyStatus updated synchronously — so it is unaffected by the publish throttle.
                scheduleRefresh()
            }
        }

        // Heartbeat: feeds only the staleness watchdog, and being a NAMED event it never reaches onmessage.
        // (A bare SSE comment would keep the proxy socket alive but fire no event at all, leaving the
        // watchdog unable to tell a live idle stream from a dead one.)
        source.addEventListener("ping", {
            noteSseMessage()
        })

        source.onerror = {
            // Fired on drop/failure. EventSource reconnects by itself, so don't tear it down — just stop
            // trusting it, which drops the poll back to 1.5s at once. If it recovers, a delivered message
            // re-promotes it.
            sseOpened = false
            demoteSse()
        }

        eventSource = source
        lastSseMessageMillis = Date.now()

        // Probe. Fires only on the opened-but-mute case (see sseOpened). The identity check keeps a stale
        // probe from tearing down a stream that was already replaced.
        async {
            delay(sseProbeMillis.toLong())
            if (eventSource === source && !sseHealthy && sseOpened) {
                sseUnavailable = true
                closeEventSource()
                rearmRefresh()
            }
        }
    }


    private fun noteSseMessage() {
        lastSseMessageMillis = Date.now()
        if (!sseHealthy) {
            // Promote: push is proven to deliver, so the poll drops to its relaxed net.
            sseHealthy = true
            rearmRefresh()
        }
    }


    private fun demoteSse() {
        if (!sseHealthy) {
            return
        }
        // Back to the fallback poll cadence immediately.
        sseHealthy = false
        rearmRefresh()
    }


    // Tear the stream down WITHOUT touching the poll cadence. Split from demoteSse deliberately: cadence
    // re-arming routes through scheduleRefresh, which itself manages the stream — so a close that re-armed
    // would re-enter itself. Callers pick the cadence they want afterwards.
    private fun closeEventSource() {
        eventSource?.close()
        eventSource = null
        sseHealthy = false
        sseOpened = false
    }


    // A dead-but-open socket delivers no error and no heartbeat — only elapsed silence reveals it. Checked
    // from the poll tick, the one loop still running when push has silently stopped delivering.
    private fun checkSseStale() {
        if (eventSource == null) {
            return
        }

        if (Date.now() - lastSseMessageMillis > sseStaleMillis) {
            closeEventSource()
            rearmRefresh()
        }
    }


    private fun onVisibilityChanged() {
        if (isDocumentVisible()) {
            // Re-sync FIRST — this tab held no stream while hidden, so its status is stale by construction.
            // scheduleRefresh then re-opens the stream if a run is still executing.
            async {
                lookupStatus()
                publish()
                rearmRefresh()
            }
        }
        else {
            // Hidden: give the connection back (the ~6-per-origin cap is shared across all tabs). No re-arm —
            // a background tab has nothing to animate, and it re-syncs on becoming visible.
            closeEventSource()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var refreshPending: Boolean = false

    // A lodash debounce bakes its interval in at construction, so the cadence cannot be retuned on an existing
    // instance — hence one instance per cadence, selected per schedule by SSE health. Both run the same body.
    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        onRefreshTick()
    }, debounceMillis)

    private val pushRefreshDebounce: FunctionWithDebounce = lodash.debounce({
        onRefreshTick()
    }, pushDebounceMillis)


    private fun onRefreshTick() {
        refreshPending = false
        async {
            // Also the watchdog's tick: a dead-but-open stream produces no event to hang this off, and this
            // loop is the only thing still running when push has silently stopped delivering.
            checkSseStale()

            // Same throttle as the pushed path. A no-op in practice (both poll cadences are already >= the
            // throttle), but routing it here keeps "which transport delivered this" from mattering.
            publishStatus(lookupStatus())

            scheduleRefresh()
        }
    }


    private fun scheduleRefresh() {
        val running = clientLogicState.isExecuting()

        // The one choke point binding the push stream's lifetime to the run: every path that arms this loop
        // (init, each control verb, each tick) passes through here, so the stream can't be forgotten on a
        // path someone adds later.
        //
        // Note "running" is isExecuting(), and a PAUSED run is NOT executing — so a parked run holds no stream
        // and no poll at all (correct: it cannot change state until a control verb, so there is nothing to
        // carry). The consequence catches the eye wrong: each control verb's own lookupStatus() is LOAD-BEARING,
        // not a pre-push leftover to be gated away on sseHealthy. It is the call that moves the state to
        // Stepping/Running so this gate opens the stream and arms the poll; without it neither would ever start
        // and the settle would never arrive.
        if (!running) {
            // Settled: give the connection back and drop any interval still armed. The cancel is deliberately
            // NOT skipped when refreshPending is set — an interval in flight is precisely what needs cancelling,
            // and gating it that way let exactly one stray poll fire after every settle. Both calls are
            // idempotent, so the already-idle case costs nothing.
            closeEventSource()
            cancelRefresh()
            return
        }

        connectEventSourceIfNeeded()

        // Don't stack an interval on top of one already in flight.
        if (refreshPending) {
            return
        }
        refreshPending = true

        // Relaxed net while push is proven to be delivering; the pre-push 1.5s otherwise. demoteSse
        // re-arms, so a stream that dies mid-run drops straight back to 1.5s.
        if (sseHealthy) {
            pushRefreshDebounce.apply()
        }
        else {
            refreshDebounce.apply()
        }
    }


    // Switch cadence NOW rather than at the end of the interval already in flight (which, coming off the
    // 10s net, could otherwise leave a dead stream unnoticed for 10s).
    private fun rearmRefresh() {
        cancelRefresh()
        scheduleRefresh()
    }


    private fun cancelRefresh() {
        refreshDebounce.cancel()
        pushRefreshDebounce.cancel()
        refreshPending = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    // [stepMode] applies only to a paused (stepping) start — it is the mode of the first step. Default
    // [StepMode.Into] is "Start Stepping"; [StepMode.Over] is "Start Stepping Over" (the Step Over button's
    // start-fresh path), which runs a sub-Logic entered on the first boundary to completion instead of descending.
    fun startAndRunAsync(
        mainLocation: ObjectLocation,
        paused: Boolean,
        pauseOnError: Boolean,
        stepMode: StepMode = StepMode.Into
    ) {
        require(!clientLogicState.isActive()) {
            "Already running"
        }
        cancelSlowLoop()

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Start,
            controlError = null)
        publish()

        async {
            delay(1)

            val error = startFailure(mainLocation) {
                if (paused) {
                    restClient.logicStartAndStep(
                        mainLocation, pauseOnError, stepMode, currentBreakpointLocations())
                }
                else {
                    restClient.logicStartAndRun(
                        mainLocation, pauseOnError, currentBreakpointLocations())
                }
            }

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None,
                controlError = error)

            if (error == null) {
                refreshAfterControl()
            }

            publish()
        }
    }


    // Every control verb acting on the active run shares this shape: publish the pending state, issue the
    // request, then settle `pending` back and surface any failure as a [ControlError].
    //
    // [documentPath] is the document the verb was aimed at, and therefore the only one its failure is shown on
    // — a run-wide verb passes [runDocumentPath], a verb aimed at one document passes that document. Getting it
    // wrong hides the failure entirely (the stage renders a control error only on the document it names), so it
    // is asked for per call rather than guessed from the run.
    //
    // The catch is load-bearing: the REST call throws on a non-2xx (carrying the server's reason), and an
    // uncaught throw inside [async] is a rejected promise nobody observes — the failure vanishes and the
    // controls stay stuck mid-action.
    private fun controlAsync(
        label: String,
        pending: ClientLogicState.Pending,
        documentPath: DocumentPath?,
        rejectedLabel: String = label,
        request: suspend (LogicRunId) -> LogicControlReply
    ) {
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = pending,
            controlError = null)
        publish()

        async {
            delay(1)

            val error =
                try {
                    val reply = request(logicRunId)
                    when (reply.response) {
                        LogicRunResponse.Submitted -> null

                        // The server understood and refused (an invalid target), which has its own wording;
                        // the reason it names is the detail.
                        LogicRunResponse.Rejected -> ControlError(rejectedLabel, reply.reason, documentPath)

                        else -> ControlError(label, reply.response.name, documentPath)
                    }
                }
                catch (t: Throwable) {
                    ControlError(label, failureDetail(t), documentPath)
                }

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None,
                controlError = error)

            if (error == null) {
                refreshAfterControl()
            }

            publish()
        }
    }


    // The read that follows a landed control verb: the run state moved, so it is re-read and the refresh
    // re-armed. A failure here is the follow-up READ failing, not the user's action, so it neither surfaces as
    // a [ControlError] nor escapes as an unobserved rejection — escaping would skip the caller's publish,
    // stranding `pending` with the poll unarmed until the NEXT control verb, which reads as a frozen ribbon
    // and a run indicator stuck on the state before the verb.
    private suspend fun refreshAfterControl() {
        try {
            delay(controlSettleMillis.toLong())
            lookupStatus()
        }
        catch (t: Throwable) {
            console.warn("Unable to read the run status", t)
        }
        scheduleRefresh()
    }


    // Both ways to start a run (plain / slow-motion) classify their outcome here: the failure to show, or null
    // on success. Scoped to the document being started rather than to the run, which doesn't exist yet.
    private suspend fun startFailure(
        mainLocation: ObjectLocation,
        request: suspend () -> LogicRunId?
    ): ControlError? {
        return try {
            when (request()) {
                null -> ControlError(startLabel, null, mainLocation.documentPath)
                else -> null
            }
        }
        catch (t: Throwable) {
            ControlError(startLabel, failureDetail(t), mainLocation.documentPath)
        }
    }


    // The document a RUN-WIDE verb (pause / continue / step / stop) belongs to: the run's root frame's. Scoping
    // its failure there keeps the error off an unrelated document the user navigates to afterwards. A verb aimed
    // at one document — move-to — names that document instead, and would be invisible under this scope.
    private fun runDocumentPath(): DocumentPath? {
        return clientLogicState.logicStatus?.active?.frame?.objectLocation?.documentPath
    }


    private fun failureDetail(failure: Throwable): String? {
        return (failure as? HttpStatusException)?.detail ?: failure.message
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun pauseAsync() {
        controlAsync("Unable to pause", ClientLogicState.Pending.Pause, runDocumentPath()) {
            LogicControlReply(restClient.logicPause(it))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun continueRunAsync() {
        controlAsync("Unable to continue", ClientLogicState.Pending.Pause, runDocumentPath()) {
            LogicControlReply(restClient.logicContinueRun(it))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Live-toggle pause-on-error on the active run (the header toggle is now clickable while paused). Fire and
    // forget: the toggle's display state lives in HeaderRunController, and the value isn't surfaced back through
    // LogicStatus — this only pushes it onto the running control so the next continue/step honours it.
    fun setPauseOnErrorAsync(pauseOnError: Boolean) {
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        async {
            restClient.logicSetPauseOnError(logicRunId, pauseOnError)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Toggle a breakpoint on a step/element (the gutter dot). Stable-id keyed so the dot follows a rename;
    // volatile, never persisted to notation. If a run is active the replace-set is pushed immediately
    // (fire and forget, like setPauseOnErrorAsync); otherwise the set rides the next run start.
    fun toggleBreakpointAsync(stepLocation: ObjectLocation) {
        val stableId = objectStableMapper.objectStableId(stepLocation)
        val breakpoints = clientLogicState.breakpoints

        clientLogicState = clientLogicState.copy(
            breakpoints =
                if (stableId in breakpoints) { breakpoints - stableId }
                else { breakpoints + stableId })
        publish()

        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        async {
            restClient.logicSetBreakpoints(logicRunId, currentBreakpointLocations())
        }
    }


    // The breakpoint set as current locations (what crosses the wire — stable ids are client-local).
    // Ids whose element no longer exists (deleted step / document) are pruned rather than pushed.
    private fun currentBreakpointLocations(): List<ObjectLocation> {
        val breakpoints = clientLogicState.breakpoints
        if (breakpoints.isEmpty()) {
            return listOf()
        }

        val locationById = breakpoints.associateWith { objectStableMapper.objectLocationOrNull(it) }

        val stale = locationById.filterValues { it == null }.keys
        if (stale.isNotEmpty()) {
            clientLogicState = clientLogicState.copy(breakpoints = breakpoints - stale)
            publish()
        }

        return locationById.values.filterNotNull()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun stepAsync() {
        controlAsync("Unable to step", ClientLogicState.Pending.Step, runDocumentPath()) {
            LogicControlReply(restClient.logicStep(it))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Step Over: like Step, but the server runs any sub-document (RunStep child) entered on this tick to
    // completion instead of descending into it, pausing at the next step of the current frame.
    fun stepOverAsync() {
        controlAsync("Unable to step over", ClientLogicState.Pending.Step, runDocumentPath()) {
            LogicControlReply(restClient.logicStepOver(it))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Step Out: run the deepest currently-paused frame (the current document) to completion, then pause
    // at the caller's next step — or, if at the run root, run to the end.
    fun stepOutAsync() {
        controlAsync("Unable to step out", ClientLogicState.Pending.Step, runDocumentPath()) {
            LogicControlReply(restClient.logicStepOut(it))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Move-to (Set Next Statement): reposition the frame [executionId] names to [target] without executing the
    // intervening steps (backward = re-run from there, forward = skip over). Unlike step / pause, both are
    // supplied by the caller (the draggable next-to-run arrow of whichever document is being viewed), not read
    // from status. A Rejected response (a structurally-invalid target, or a frame the server can't reposition)
    // is surfaced distinctly from other control failures, carrying the server's reason as the detail.
    //
    // Scoped to the TARGET's document, not the run's root: the user drags the marker in whichever document they
    // are viewing, and in a nested frame that is not the root — scoping to the root would show the refusal on a
    // document the user isn't looking at, and hide it on the one they acted in.
    fun moveToAsync(target: ObjectLocation, executionId: LogicExecutionId) {
        controlAsync(
            "Unable to move", ClientLogicState.Pending.Step, target.documentPath, moveRejectedLabel
        ) {
            restClient.logicMoveTo(it, target, executionId)
        }
    }


    // A move the CLIENT ruled out without asking (the drag handle knows the frame spine can't carry it — see
    // ScriptExecutionMargin.spineRefusal). Surfaced through the same label, panel and document scoping as a
    // server refusal, so the two are one error surface: a refusal the client caught early must still say why,
    // or the user is back to a drop that does nothing for no stated reason.
    fun refuseMove(documentPath: DocumentPath, reason: String) {
        clientLogicState = clientLogicState.copy(
            controlError = ControlError(moveRejectedLabel, reason, documentPath))
        publish()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // "Slow motion" run: the browser auto-issues Step with a fixed dwell, so each step's result is
    // visible before the next. Pure client pacing over a normal stepped run — nothing server-side.
    //
    // Termination: the loop stops when the run finishes (status active == null — covers success and,
    // with pause-on-error off, a failing step that terminates the run) or when any manual control or
    // pauseSlowAsync() clears the slowLooping flag. KNOWN LIMITATION: with pause-on-error ON, a step
    // that deterministically fails stays paused (non-terminal) and is re-issued each cycle; the loop is
    // slow and fully cancellable (Pause/Stop/etc.), not a hang, and this cannot occur with the default
    // pause-on-error off.
    //
    // The loop auto-issues Step by default; with stepOver = true it issues Step Over instead, so it
    // paces step-by-step WITHIN the current document without descending into nested logic. Toggling the
    // other variant while already looping just switches the mode in-flight (the loop reads the flag).
    fun slowRunAsync(mainLocation: ObjectLocation, pauseOnError: Boolean, stepOver: Boolean = false) {
        if (clientLogicState.slowLooping) {
            if (clientLogicState.slowStepOver != stepOver) {
                clientLogicState = clientLogicState.copy(slowStepOver = stepOver)
                publish()
            }
            return
        }

        clientLogicState = clientLogicState.copy(
            slowLooping = true,
            slowStepOver = stepOver,
            controlError = null)
        publish()

        async {
            if (!clientLogicState.isActive()) {
                // Start a fresh run in stepping (paused) mode; this also executes the first step. In step-over
                // mode the bootstrap step is itself a Step Over, so the run never descends into a sub-Logic on
                // the first tick (without this it would descend on the bootstrap and only climb out on the first
                // subsequent Step Over — see ServerLogicController.startStep).
                val error = startFailure(mainLocation) {
                    restClient.logicStartAndStep(
                        mainLocation, pauseOnError,
                        if (stepOver) StepMode.Over else StepMode.Into,
                        currentBreakpointLocations())
                }

                if (error != null) {
                    clientLogicState = clientLogicState.copy(
                        slowLooping = false,
                        controlError = error)
                    publish()
                    return@async
                }
                delay(10.milliseconds)
                lookupStatus()
                // Open the push stream for the bootstrap step, exactly as each manual control verb does
                // (state is now Stepping, so scheduleRefresh's gate opens it). awaitStepSettled then rides
                // push instead of polling once the stream proves healthy. See runSlowLoop.
                scheduleRefresh()
                awaitStepSettled()
                publish()
            }

            runSlowLoop()
        }
    }


    // Stop the slow-motion loop after its current step; the run stays Paused (so the user can then Step,
    // continue at full speed, resume slow-motion, or Stop).
    fun pauseSlowAsync() {
        if (!clientLogicState.slowLooping) {
            return
        }
        clientLogicState = clientLogicState.copy(slowLooping = false, slowStepOver = false)
        publish()
    }


    private suspend fun runSlowLoop() {
        while (clientLogicState.slowLooping) {
            // Stop once the run finished / was cancelled.
            if (!clientLogicState.isActive()) {
                break
            }

            // Stop on a deliberate halt — a Pause step or pause-on-error — rather than auto-stepping past it.
            // Covers a fresh start whose first step halts (slowRunAsync settles before calling here) and every
            // subsequent step (awaitStepSettled refreshes status before the loop re-checks).
            if (clientLogicState.isHaltPaused()) {
                break
            }

            // The visible dwell between steps.
            delay(slowPacingMillis.toLong())

            // The user may have toggled slow-motion off (or taken manual control) during the dwell.
            if (!clientLogicState.slowLooping || !clientLogicState.isActive()) {
                break
            }

            val logicRunId = clientLogicState.logicStatus?.active?.id
                ?: break
            val response =
                if (clientLogicState.slowStepOver) {
                    restClient.logicStepOver(logicRunId)
                }
                else {
                    restClient.logicStep(logicRunId)
                }
            if (response != LogicRunResponse.Submitted) {
                break
            }

            // Move local state to Stepping so scheduleRefresh's isExecuting() gate opens the push stream
            // for this step (load-bearing, exactly as stepAsync/stepOverAsync do — see scheduleRefresh's
            // note at the !running gate). awaitStepSettled then rides push when the stream is healthy
            // instead of polling status; the stream closes again on settle for the dwell, reopens next step.
            lookupStatus()
            scheduleRefresh()

            awaitStepSettled()
            publish()
        }

        if (clientLogicState.slowLooping) {
            clientLogicState = clientLogicState.copy(slowLooping = false, slowStepOver = false)
        }
        publish()
    }


    // Wait until the in-flight step has settled back to Paused (no longer Stepping) or the run finished
    // (active == null), bounded by a defensive cap.
    //
    // Observers (the sidebar run-highlight + depth badge, the frame tree) must see the intermediate frames a
    // single step traverses while it's mid-flight — e.g. a stepped-over RunStep child lit up in the sidebar
    // while it runs (its Wait). Without that, slow motion looks frozen even though each step transiently
    // descended.
    //
    // Both transports deliver those intermediates, from opposite directions:
    //   - push healthy: the server already publishes every status change (the settle included — see
    //     ServerLogicController.settleAfterDrive), so this loop only watches locally-updated state and issues
    //     NO requests at all, showing the frames the engine actually passed through rather than a
    //     slowSettlePollMillis sampling of them. (slowRunAsync / runSlowLoop arm the stream per step, so this
    //     branch is the norm whenever push is available.)
    //   - push absent/dead: fall back to polling status here at slowSettlePollMillis and publishing each poll.
    private suspend fun awaitStepSettled() {
        var waited = 0
        while (waited < slowSettleMaxMillis) {
            delay(slowSettlePollMillis.toLong())
            waited += slowSettlePollMillis

            if (!sseHealthy) {
                // Through the same throttle as the pushed path, so a dead stream changes the SAMPLING rate,
                // not the repaint rule. The settle itself is a state change, so it still lands immediately.
                publishStatus(lookupStatus())
            }

            // Reads locally-applied state, not the published state, so the throttle cannot delay the settle.
            val active = clientLogicState.logicStatus?.active
            if (active == null || active.state != LogicRunState.Stepping) {
                return
            }
        }
    }


    private fun cancelSlowLoop() {
        if (clientLogicState.slowLooping) {
            clientLogicState = clientLogicState.copy(slowLooping = false, slowStepOver = false)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun stopAsync() {
        controlAsync("Unable to stop", ClientLogicState.Pending.Cancel, runDocumentPath()) {
            LogicControlReply(restClient.logicCancel(it))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Whether the logic trace store has a most-recent run retained for this document (i.e. there is
    // something to clear). Mirrors FlowProgressStore.mostRecent / ScriptProgressStore.mostRecentQuery.
    suspend fun traceMostRecentPresent(mainLocation: ObjectLocation): Boolean {
        val result = restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to mainLocation.documentPath.asString(),
            LogicConventions.paramSubObjectPath to mainLocation.objectPath.asString()
        )

        return when (result) {
            is ExecutionSuccess ->
                result.value.get() != null

            is ExecutionFailure ->
                false
        }
    }


    // Clear the retained logic trace for this document via the generic LogicTraceEndpoint reset, then
    // re-poll status and publish: the clear bumps the controller's epoch, which is what makes every Logic
    // document's progress fetch key move so they repaint to empty (architecture.md § 3).
    fun clearTraceAsync(mainLocation: ObjectLocation) {
        if (clientLogicState.isActive()) {
            return
        }

        async {
            val result = restClient.performDetached(
                LogicConventions.logicTraceEndpointLocation,
                CommonRestApi.paramAction to LogicConventions.actionReset,
                LogicConventions.paramSubDocumentPath to mainLocation.documentPath.asString(),
                LogicConventions.paramSubObjectPath to mainLocation.objectPath.asString()
            )

            if (result is ExecutionFailure) {
                clientLogicState = clientLogicState.copy(
                    controlError = ControlError(
                        clearTraceLabel, result.errorMessage, mainLocation.documentPath))
            }

            lookupStatus()
            publish()
        }
    }


    // Memo for tracedDocuments, keyed by the trace version the answer was fetched against.
    private var tracedDocumentsVersion: String? = null
    private var tracedDocumentsQuery: CompletableDeferred<Set<DocumentPath>>? = null


    // Every document that currently holds a retained logic trace (run roots + RunStep sub-logic roots).
    // Drives the sidebar's "has trace" indicator (ProjectController) and the Clear button's enablement
    // (HeaderRunController). Empty on failure.
    //
    // Memoized per trace version because those two callers ask this identical question off the SAME
    // traceVersion() and neither knows the other exists — so without the memo the query goes out twice on
    // every version change. The cache holds the in-flight query, not just its result: the callers race (both
    // enter before either completes), so caching only settled answers would still let both requests leave.
    //
    // The memo dedupes; it does not rate-limit. Keyed on structureVersion (NOT traceVersion) — the
    // structure-keyed query gate of architecture.md § 3: the traced-document set changes only when a
    // document first appears in the run.
    suspend fun tracedDocuments(): Set<DocumentPath> {
        val version = clientLogicState.structureVersion()

        val inFlight = tracedDocumentsQuery
        if (inFlight != null && tracedDocumentsVersion == version) {
            return inFlight.await()
        }

        // Published before the first suspension point below — safe because JS is single-threaded, so a second
        // caller cannot enter until this one suspends, by which point it sees the query to join.
        val query = CompletableDeferred<Set<DocumentPath>>()
        tracedDocumentsVersion = version
        tracedDocumentsQuery = query

        val fetched =
            try {
                lookupTracedDocuments()
            }
            catch (e: Throwable) {
                // Never strand a joined caller awaiting this query, and never cache a failure as an answer.
                tracedDocumentsVersion = null
                tracedDocumentsQuery = null
                query.complete(emptySet())
                throw e
            }

        query.complete(fetched)
        return fetched
    }


    private suspend fun lookupTracedDocuments(): Set<DocumentPath> {
        val result = restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            CommonRestApi.paramAction to LogicConventions.actionTraced
        )

        return when (result) {
            is ExecutionSuccess -> {
                val documentPathStrings = result.value.get() as? List<*>
                    ?: emptyList<Any?>()

                documentPathStrings
                    .mapNotNull { it as? String }
                    .map { DocumentPath.parse(it) }
                    .toSet()
            }

            is ExecutionFailure ->
                emptySet()
        }
    }


    // Clear ALL retained traces (the run controls are global). Mirrors clearTraceAsync but resets every
    // document; the epoch bump then makes every Logic document's progress repaint to empty.
    fun clearAllTracesAsync() {
        if (clientLogicState.isActive()) {
            return
        }

        async {
            val result = restClient.performDetached(
                LogicConventions.logicTraceEndpointLocation,
                CommonRestApi.paramAction to LogicConventions.actionResetAll
            )

            if (result is ExecutionFailure) {
                clientLogicState = clientLogicState.copy(
                    controlError = ControlError(clearTraceLabel, result.errorMessage, null))
            }

            lookupStatus()
            publish()
        }
    }
}