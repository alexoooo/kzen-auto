package tech.kzen.auto.client.service.logic

import kotlinx.coroutines.delay
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunResponse
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
    fun startAndRunAsync(mainLocation: ObjectLocation, paused: Boolean) {
        require(! clientLogicState.isActive()) {
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
                    ClientContext.restClient.logicStartAndStep(mainLocation)
                }
                else {
                    ClientContext.restClient.logicStartAndRun(mainLocation)
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
            val response = ClientContext.restClient.logicPause(logicRunId)

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
            val response = ClientContext.restClient.logicContinueRun(logicRunId)

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
            val response = ClientContext.restClient.logicStep(logicRunId)

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
            val response = ClientContext.restClient.logicCancel(logicRunId)

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
}