package tech.kzen.auto.client.service.logic

import kotlinx.coroutines.delay
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.lib.common.model.locate.ObjectLocation


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

        val running = isActive()

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.None)

        publish()

        if (running) {
            scheduleRefresh()
        }
    }


    private fun isActive(): Boolean {
        return clientLogicState.logicStatus?.active != null
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
        val running = isActive()
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
    fun startAndRunAsync(mainLocation: ObjectLocation) {
        require(! clientLogicState.isActive()) {
            "Already running"
        }

        clientLogicState = clientLogicState.copy(
            pending = ClientLogicState.Pending.Start,
            controlError = null)
        publish()

        async {
            delay(1)
            val logicRunId = ClientContext.restClient.logicStart(mainLocation)

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
}