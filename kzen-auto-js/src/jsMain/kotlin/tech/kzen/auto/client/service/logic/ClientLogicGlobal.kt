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
import tech.kzen.lib.common.model.location.ObjectLocation


class ClientLogicGlobal(
    private val restClient: ClientRestApi
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val debounceMillis = 1_500
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
    fun stopAsync() {
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
}