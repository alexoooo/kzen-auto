package tech.kzen.auto.client.service.logic

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.w3c.dom.EventSource
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.engine.StepMode
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
        // Status-poll cadence while the push stream is NOT proven to be delivering. Unchanged from the
        // pre-push behaviour on purpose: it is the floor this transport degrades to, so a broken or buffered
        // stream can never leave the UI slower than it was before push existed.
        private const val debounceMillis = 1_500

        // Status-poll cadence while the push stream IS proven healthy. Not zero: a relaxed safety net that
        // re-syncs if a push is ever missed, cheap enough to be irrelevant.
        private const val pushDebounceMillis = 10_000

        // Ceiling on how often an arriving status may fan out to observers, for statuses that carry ONLY a
        // new trace sequence (see publishStatus). A structure change ignores this and publishes at once.
        //
        // Sized by what a human can use, not by what the engine can produce: a full-speed run is a blur at
        // any cadence, and each publish costs ~4 REST round trips downstream. Measured pre-throttle, a 48s
        // FizzBuzz run pushed ~165 statuses (~3.4/s) and cost 433 requests; at 1s that is ~200. Stepping is
        // unaffected — every step boundary is a state change, hence a structure change.
        private const val statusPublishThrottleMillis = 1_000

        // A just-opened stream must DELIVER within this or it isn't trusted. Deliberately not keyed off
        // onopen: an intermediary that buffers the response opens the stream perfectly well and then delivers
        // nothing, which looks identical to a healthy idle stream. The server sends the current status
        // immediately on connect precisely so this probe has something to wait for.
        private const val sseProbeMillis = 3_000

        // Nothing at all (not even a heartbeat) for this long ⇒ the stream is dead-but-open; drop back to
        // polling and reconnect. 3x the server's 15s heartbeat, so it tolerates two lost beats.
        private const val sseStaleMillis = 45_000

        // "Slow motion" auto-step pacing: the visible dwell between auto-issued steps, and the cadence /
        // cap used to wait for each step to settle back to Paused before issuing the next.
        private const val slowPacingMillis = 750
        private const val slowSettlePollMillis = 50
        private const val slowSettleMaxMillis = 30_000
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
    // Fan-out throttle for statuses arriving from the TRANSPORT (pushed or polled — one rule, either courier).
    // Control verbs deliberately bypass it: they are one per user action and must land immediately.
    //
    // Why this exists at all: the engine bumps the run's sequence on every emit, so a status arrives ~3.4/s
    // during an active script — and each publish() fans out into ~4 REST round trips (lookup / run-history /
    // traced / run-executions). Nobody can read 3 trace repaints a second, so paying 12 requests/s for them is
    // pure waste. Throttling HERE rather than at each query is what makes that one decision instead of four:
    // every downstream view is publish-driven, so they all inherit the cadence and none needs its own clock.
    //
    // throttle (leading + trailing), NOT debounce: a run is a continuous stream, and a trailing-only debounce
    // would publish nothing at all until the run stopped.
    private val publishStatusThrottle: FunctionWithDebounce = lodash.throttle({
        publish()
    }, statusPublishThrottleMillis)


    private fun publishStatus(structureChanged: Boolean) {
        // A structure change is a transition the user is WAITING on — a run started, settled, changed state
        // (every step boundary is one of these), or a trace was cleared. Never throttled, and it resets the
        // throttle's clock so the next value that arrives repaints at once: that is what keeps a stepped run
        // showing its first intermediate frame instantly instead of up to a second late.
        if (structureChanged) {
            publishStatusThrottle.cancel()
            publish()
            return
        }

        // Sequence-only: new values, same shape. The trailing edge is what makes this safe to defer — the
        // last status of a burst always publishes ~1s later even if nothing else ever arrives, so a value can
        // never be stranded unshown by a run that goes quiet mid-flight.
        publishStatusThrottle.apply()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Push transport. The EventSource carries the SAME LogicStatus payload the poll fetches, so a pushed status
    // is applied through the identical path — push is a faster courier, not a second protocol. The poll loop
    // stays armed as an adaptive fallback (see scheduleRefresh), so every failure mode of this stream degrades
    // to the pre-push behaviour instead of freezing the UI.
    private var eventSource: EventSource? = null

    // Delivery-PROVEN, never connection-proven: set only by a message actually arriving, because a buffering
    // intermediary opens the stream fine and delivers nothing. Drives the poll cadence and lets the
    // slow-motion settle wait skip its network polling.
    private var sseHealthy: Boolean = false
    private var lastSseMessageMillis: Double = 0.0

    // Latched when a stream OPENS but delivers nothing within the probe window — the signature of a buffering
    // intermediary. Push is then given up on for this page's life and we stay on the 1.5s poll. The latch is
    // required for termination: the probe's own teardown re-arms the refresh loop, which reconnects, which
    // fails the probe again — a permanent 3s reconnect cycle against exactly the intermediary the probe
    // exists to detect. It is the right shape because a buffering proxy is a static property of the
    // deployment, not a transient fault, and a false positive costs only "pre-push behaviour until reload".
    private var sseUnavailable: Boolean = false

    // Whether the CURRENT connection reached open. This is what separates the two silent failures: opened but
    // mute ⇒ buffering ⇒ latch; never opened ⇒ the server/network is down ⇒ do NOT latch and do not close —
    // EventSource retries by itself, so a backend that comes back is picked up automatically (we sit demoted
    // on the 1.5s poll meanwhile, and a delivered message re-promotes).
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

        // Only the visible tab holds a stream open. This is the mitigation for the browser's ~6-connections
        // -per-origin HTTP/1.1 cap, which is shared across EVERY tab of this origin (in the packaged product
        // that is the shell: the launcher and every project). A background tab has nothing to animate, and
        // re-syncs on becoming visible, so the realistic worst case is one connection per window.
        if (! isDocumentVisible()) {
            return
        }

        // Nothing to watch: no run is executing. Matches scheduleRefresh's gate.
        if (! clientLogicState.isExecuting()) {
            return
        }

        val source = EventSource(restClient.logicEventsUrl())
        sseOpened = false

        // Deliberately does NOT mark the stream healthy — that is the whole point of the probe. A buffering
        // intermediary opens the connection perfectly and then delivers nothing, so "open" proves only that
        // something accepted the request. Only an arriving message proves delivery.
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

        // Heartbeat. Only feeds the staleness watchdog — it carries no status, and being a NAMED event it
        // never reaches onmessage. (A bare SSE comment would keep the proxy socket alive but fire no event at
        // all, leaving the watchdog unable to tell a live idle stream from a dead one.)
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

        // Probe. Fires only on the opened-but-mute case (see sseOpened): that is a buffering intermediary, and
        // no amount of reconnecting will fix it, so latch push off and stay on the 1.5s poll. A stream that
        // never opened is left alone to retry itself. The identity check keeps a stale probe from tearing down
        // a stream that was already replaced.
        async {
            delay(sseProbeMillis.toLong())
            if (eventSource === source && ! sseHealthy && sseOpened) {
                sseUnavailable = true
                closeEventSource()
                rearmRefresh()
            }
        }
    }


    private fun noteSseMessage() {
        lastSseMessageMillis = Date.now()
        if (! sseHealthy) {
            // Promote: push is proven to deliver, so the poll drops to its relaxed net.
            sseHealthy = true
            rearmRefresh()
        }
    }


    private fun demoteSse() {
        if (! sseHealthy) {
            return
        }
        // Back to the pre-push cadence, immediately — never leave the UI slower than it used to be.
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

            // Same throttle as the pushed path — push is a faster courier, not a second protocol. A no-op in
            // practice (both poll cadences are already >= the throttle), but routing it here keeps the rule
            // in one place rather than making "which transport delivered this" matter.
            publishStatus(lookupStatus())

            scheduleRefresh()
        }
    }


    private fun scheduleRefresh() {
        val running = clientLogicState.isExecuting()
//        println("#@%$ scheduleRefresh - $running")

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
        if (! running) {
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
            val logicRunId =
                if (paused) {
                    restClient.logicStartAndStep(
                        mainLocation, pauseOnError, stepMode, currentBreakpointLocations())
                }
                else {
                    restClient.logicStartAndRun(
                        mainLocation, pauseOnError, currentBreakpointLocations())
                }

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (logicRunId == null) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to start")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun pauseAsync() {
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Pause,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicPause(logicRunId)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to stop")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun continueRunAsync() {
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Pause,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicContinueRun(logicRunId)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to stop")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
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
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Step,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicStep(logicRunId)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to step")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Step Over: like Step, but the server runs any sub-document (RunStep child) entered on this tick to
    // completion instead of descending into it, pausing at the next step of the current frame.
    fun stepOverAsync() {
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Step,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicStepOver(logicRunId)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to step over")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Step Out: run the deepest currently-paused frame (the current document) to completion, then pause
    // at the caller's next step — or, if at the run root, run to the end.
    fun stepOutAsync() {
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Step,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicStepOut(logicRunId)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to step out")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Move-to (Set Next Statement): reposition the paused run to [target] without executing the intervening steps
    // (backward = re-run from there, forward = skip over). Unlike step / pause, [target] is supplied by the caller
    // (the draggable next-to-run arrow / "Set next step here" action — phase 3), not read from status. A Rejected
    // response (unsupported / structurally-invalid target) is surfaced distinctly from other control failures.
    fun moveToAsync(target: ObjectLocation) {
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Step,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicMoveTo(logicRunId, target)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError =
                        if (response == LogicRunResponse.Rejected) "Can't move to this step"
                        else "Unable to move")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
        }
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
            if (! clientLogicState.isActive()) {
                // Start a fresh run in stepping (paused) mode; this also executes the first step. In step-over
                // mode the bootstrap step is itself a Step Over, so the run never descends into a sub-Logic on
                // the first tick (without this it would descend on the bootstrap and only climb out on the first
                // subsequent Step Over — see ServerLogicController.startStep).
                val logicRunId = restClient.logicStartAndStep(
                    mainLocation, pauseOnError,
                    if (stepOver) StepMode.Over else StepMode.Into,
                    currentBreakpointLocations())
                if (logicRunId == null) {
                    clientLogicState = clientLogicState.copy(
                        slowLooping = false,
                        controlError = "Unable to start")
                    publish()
                    return@async
                }
                delay(10.milliseconds)
                lookupStatus()
                awaitStepSettled()
                publish()
            }

            runSlowLoop()
        }
    }


    // Stop the slow-motion loop after its current step; the run stays Paused (so the user can then Step,
    // continue at full speed, resume slow-motion, or Stop).
    fun pauseSlowAsync() {
        if (! clientLogicState.slowLooping) {
            return
        }
        clientLogicState = clientLogicState.copy(slowLooping = false, slowStepOver = false)
        publish()
    }


    private suspend fun runSlowLoop() {
        while (clientLogicState.slowLooping) {
            // Stop once the run finished / was cancelled.
            if (! clientLogicState.isActive()) {
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
            if (! clientLogicState.slowLooping || ! clientLogicState.isActive()) {
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
    //     ServerLogicController.settleAfterDrive), and publishStatus publishes on arrival. So this loop only
    //     watches locally-updated state and issues NO requests at all — and the frames it shows are the ones
    //     the engine actually passed through rather than a 50ms sampling of them, subject to publishStatus's
    //     throttle: each step's boundary and first intermediate land at once, later ones within the SAME step
    //     at the throttle's cadence. A step boundary is a state change, so it always resets that clock.
    //   - push absent/dead: fall back to polling status here at 50ms and publishing each poll, exactly as
    //     before push existed.
    private suspend fun awaitStepSettled() {
        var waited = 0
        while (waited < slowSettleMaxMillis) {
            delay(slowSettlePollMillis.toLong())
            waited += slowSettlePollMillis

            if (! sseHealthy) {
                // Through the same throttle as the pushed path, so a dead stream changes the SAMPLING rate,
                // not the repaint rule. Without it this publishes at 20/s — ~80 REST calls/s downstream —
                // where push publishes ~1/s for the same run. The settle itself is a state change, so it
                // still lands immediately.
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
        cancelSlowLoop()
        val logicRunId = clientLogicState.logicStatus?.active?.id
            ?: return

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Cancel,
            controlError = null)
        publish()

        async {
            delay(1)
            val response = restClient.logicCancel(logicRunId)

            clientLogicState = clientLogicState.copy(
                pending = ClientLogicState.Pending.None)

            if (response != LogicRunResponse.Submitted) {
                clientLogicState = clientLogicState.copy(
                    controlError = "Unable to stop")
            }
            else {
                delay(10)
                lookupStatus()
                scheduleRefresh()
            }

            publish()
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
    // re-poll status and publish: the clear bumps the controller's epoch, which changes every Logic
    // document's progress fetch key (ClientLogicState.traceVersion), so they repaint to the now-empty
    // trace. The epoch is what carries this — status reports no active run both before and after a
    // clear, so nothing else in the response differs.
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
                    controlError = result.errorMessage)
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
    // The memo dedupes; it does not rate-limit. Cadence is bounded upstream by the status publish throttle —
    // both callers are publish-driven, so this is asked ~1/s during a run rather than per engine emit.
    suspend fun tracedDocuments(): Set<DocumentPath> {
        val version = clientLogicState.traceVersion()

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
                    controlError = result.errorMessage)
            }

            lookupStatus()
            publish()
        }
    }
}