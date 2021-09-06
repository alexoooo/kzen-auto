package tech.kzen.auto.client.objects.document.pipeline.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.pipeline.analysis.model.PipelineAnalysisStore
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputStore
import tech.kzen.auto.client.objects.document.pipeline.run.model.PipelineRunStore
import tech.kzen.auto.client.objects.document.report.state.ReportStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation


class PipelineStore: SessionGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val debounceMillis = 1_500
//        const val debounceMillis = 2_500
//        const val debounceMillis = 5_000
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onPipelineState(pipelineState: PipelineState/*, initial: Boolean*/)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var observer: Observer? = null
    private var mounted = false
    private var state: PipelineState? = null


    private var refreshPending: Boolean = false
    private var previousRunning: Boolean = false
    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        refreshPending = false
        async {
            refresh()
            scheduleRefresh()
        }
    }, ReportStore.debounceMillis)


    val input = PipelineInputStore(this)
    val analysis = PipelineAnalysisStore(this)
    val output = PipelineOutputStore(this)
    val run = PipelineRunStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun didMount(subscriber: Observer) {
        this.observer = subscriber
        mounted = true

        async {
            ClientContext.sessionGlobal.observe(this)
        }
    }


    fun willUnmount() {
        observer = null
        mounted = false
        state = null

        ClientContext.sessionGlobal.unobserve(this)
        cancelRefresh()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        if (! mounted) {
            return
        }

        val pipelineMainLocation = PipelineState.tryMainLocation(clientState)
            ?: return

        val pipelineMainDefinition = mainDefinition(clientState, pipelineMainLocation)

        val previousState = state
        val nextState = when {
            previousState == null || pipelineMainLocation != previousState.mainLocation ->
                PipelineState(
                    pipelineMainLocation,
                    pipelineMainDefinition)

            else ->
                previousState.copy(mainDefinition = pipelineMainDefinition)
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        if (state != nextState) {
            state = nextState
            observer?.onPipelineState(nextState/*, initial*/)
        }

        if (initial) {
            cancelRefresh()
            initAsync()
        }
    }


    private fun mainDefinition(clientState: SessionState, mainLocation: ObjectLocation): ObjectDefinition {
        return clientState
            .graphDefinitionAttempt
            .objectDefinitions[mainLocation]!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun initAsync() {
        async {
            delay(10)
            if (state == null) {
                return@async
            }

            input.init()
            run.init()
            output.init()
        }
    }


    private suspend fun refresh() {
        run.refresh()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): PipelineState {
        return state
            ?: throw IllegalStateException("Get state before initialized")
    }


    fun mainLocation(): ObjectLocation {
        return state().mainLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun update(updater: (PipelineState) -> PipelineState) {
        val initializedState = state
            ?: throw IllegalStateException("Update before initialized")

        val updated = updater(initializedState)

        if (state != updated) {
            state = updated
            observer?.onPipelineState(updated/*, false*/)
            scheduleRefresh()
        }
    }


    fun update(state: PipelineState) {
        if (this.state != state) {
            this.state = state
            observer?.onPipelineState(state/*, false*/)
            scheduleRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scheduleRefresh() {
        val running = state?.run?.logicStatus?.active != null
//        console.log("^^^^ scheduleRefresh: $running - $refreshPending")

        if (refreshPending) {
            return
        }

        if (running) {
            refreshPending = true
            refreshDebounce.apply()
        }
        else if (previousRunning) {
            cancelRefresh()
            output.lookupOutputWithFallbackAsync()
//            run.lookupProgressOfflineAsync()
        }
        previousRunning = running
    }


    private fun cancelRefresh() {
        refreshDebounce.cancel()
        refreshPending = false
    }
}