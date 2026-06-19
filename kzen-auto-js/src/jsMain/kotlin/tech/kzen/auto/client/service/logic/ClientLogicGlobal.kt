package tech.kzen.auto.client.service.logic

import kotlinx.coroutines.delay
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


class ClientLogicGlobal(
    private val restClient: ClientRestApi
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val debounceMillis = 1_500

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

        if (running) {
            scheduleRefresh()
        }
    }


    private suspend fun lookupStatus() {
        val logicStatus = restClient.logicStatus()

        clientLogicState = clientLogicState.copy(
            logicStatus = logicStatus)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var refreshPending: Boolean = false
    private var previousRunning: Boolean = false
    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        refreshPending = false
        async {
            lookupStatus()
            publish()

            scheduleRefresh()
        }
    }, debounceMillis)


    private fun scheduleRefresh() {
        val running = clientLogicState.isExecuting()
//        println("#@%$ scheduleRefresh - $running")

        if (refreshPending) {
            return
        }

        if (running) {
            refreshPending = true
            refreshDebounce.apply()
        }
        else if (previousRunning) {
            cancelRefresh()
        }
        previousRunning = running
    }


    private fun cancelRefresh() {
        refreshDebounce.cancel()
        refreshPending = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startAndRunAsync(mainLocation: ObjectLocation, paused: Boolean, pauseOnError: Boolean) {
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
                    restClient.logicStartAndStep(mainLocation, pauseOnError)
                }
                else {
                    restClient.logicStartAndRun(mainLocation, pauseOnError)
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
    // "Slow motion" run: the browser auto-issues Step repeatedly with a fixed dwell between steps, so
    // each step's result is visible before the next (reintroduces the old paced dataflow run-loop). Pure
    // client pacing — no server/REST change; the run is just a normal stepped run.
    //
    // Termination: the loop stops when the run finishes (status active == null — covers success and,
    // with pause-on-error off, a failing step that terminates the run) or when any manual control or
    // pauseSlowAsync() clears the slowLooping flag. KNOWN LIMITATION: with pause-on-error ON, a step
    // that deterministically fails stays paused (non-terminal) and is re-issued each cycle; the loop is
    // slow and fully cancellable (Pause/Stop/etc.), not a hang, and this cannot occur with the default
    // pause-on-error off.
    fun slowRunAsync(mainLocation: ObjectLocation, pauseOnError: Boolean) {
        if (clientLogicState.slowLooping) {
            return
        }

        clientLogicState = clientLogicState.copy(
            slowLooping = true,
            controlError = null)
        publish()

        async {
            if (! clientLogicState.isActive()) {
                // Start a fresh run in stepping (paused) mode; this also executes the first step.
                val logicRunId = restClient.logicStartAndStep(mainLocation, pauseOnError)
                if (logicRunId == null) {
                    clientLogicState = clientLogicState.copy(
                        slowLooping = false,
                        controlError = "Unable to start")
                    publish()
                    return@async
                }
                delay(10)
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
        clientLogicState = clientLogicState.copy(slowLooping = false)
        publish()
    }


    private suspend fun runSlowLoop() {
        while (clientLogicState.slowLooping) {
            // Stop once the run finished / was cancelled.
            if (! clientLogicState.isActive()) {
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
            val response = restClient.logicStep(logicRunId)
            if (response != LogicRunResponse.Submitted) {
                break
            }

            awaitStepSettled()
            publish()
        }

        if (clientLogicState.slowLooping) {
            clientLogicState = clientLogicState.copy(slowLooping = false)
        }
        publish()
    }


    // Poll status until the in-flight step has settled back to Paused (no longer Stepping) or the run
    // finished (active == null), bounded by a defensive cap.
    private suspend fun awaitStepSettled() {
        var waited = 0
        while (waited < slowSettleMaxMillis) {
            delay(slowSettlePollMillis.toLong())
            waited += slowSettlePollMillis

            lookupStatus()

            val active = clientLogicState.logicStatus?.active
            if (active == null || active.state != LogicRunState.Stepping) {
                return
            }
        }
    }


    private fun cancelSlowLoop() {
        if (clientLogicState.slowLooping) {
            clientLogicState = clientLogicState.copy(slowLooping = false)
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
    // re-poll status and publish: the fresh LogicStatus.time bumps every Logic document's progress
    // fetch key (ScriptStore / FlowController), so they repaint to the now-empty trace.
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


    // Every document that currently holds a retained logic trace (run roots + RunStep sub-logic roots).
    // Drives the sidebar's "has trace" indicator. Empty on failure.
    suspend fun tracedDocuments(): Set<DocumentPath> {
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
    // document; the fresh LogicStatus.time then makes every Logic document's progress repaint to empty.
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