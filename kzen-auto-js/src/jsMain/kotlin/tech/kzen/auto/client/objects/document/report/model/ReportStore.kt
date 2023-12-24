package tech.kzen.auto.client.objects.document.report.model

import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaStore
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.objects.document.report.preview.model.ReportPreviewStore
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.location.ObjectLocation


class ReportStore: ClientStateGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val debounceMillis = 1_500
//        const val debounceMillis = 2_500
//        const val debounceMillis = 5_000
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onReportState(reportState: ReportState/*, initial: Boolean*/)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var observer: Observer? = null
    private var mounted = false
    private var state: ReportState? = null

    private var previousLogicTime: Instant = Instant.DISTANT_PAST

    val input = ReportInputStore(this)
    val formula = ReportFormulaStore(this)
    val filter = ReportFilterStore(this)
    val analysis = ReportAnalysisStore(this)
    val previewFiltered = ReportPreviewStore(this)
    val output = ReportOutputStore(this)
    val run = ReportRunStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun didMount(subscriber: Observer) {
        this.observer = subscriber
        mounted = true

        async {
            ClientContext.clientStateGlobal.observe(this)
        }
    }


    fun willUnmount() {
        observer = null
        mounted = false
        state = null

        ClientContext.clientStateGlobal.unobserve(this)
        cancelRefresh()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        if (! mounted) {
            return
        }

        val reportMainLocation = ReportState.tryMainLocation(clientState)
            ?: return

        val reportMainDefinition = mainDefinition(clientState, reportMainLocation)

        val previousState = state
        val nextState = when {
            previousState == null || reportMainLocation != previousState.mainLocation ->
                ReportState(
                    reportMainLocation,
                    reportMainDefinition,
                    clientState.clientLogicState)

            else ->
                previousState.copy(
                    mainDefinition = reportMainDefinition,
                    clientLogicState = clientState.clientLogicState
                )
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        if (state != nextState) {
            state = nextState
            observer?.onReportState(nextState/*, initial*/)
        }

        if (initial) {
            cancelRefresh()
            initAsync()
        }
        else {
//            println("Time: $previousLogicTime | Active: ${clientState.clientLogicState.isActive()}")

            val logicTime: Instant = clientState.clientLogicState.logicStatus?.time ?: Instant.DISTANT_PAST
            if (previousLogicTime != logicTime ||
                previousLogicTime != Instant.DISTANT_PAST && ! clientState.clientLogicState.isActive()
            ) {
//                println("Scheduling $logicTime")
                previousLogicTime = logicTime
                scheduleRefresh()
            }
        }
    }


    private fun mainDefinition(clientState: ClientState, mainLocation: ObjectLocation): ObjectDefinition {
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

            output.init()
            run.init()
            input.init()
            formula.validateAsync()
            previewFiltered.init()
        }
    }


    private suspend fun refresh() {
        run.refresh()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): ReportState {
        return state
            ?: throw IllegalStateException("Get state before initialized")
    }


    fun mainLocation(): ObjectLocation {
        return state().mainLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun update(updater: (ReportState) -> ReportState) {
        val initializedState = state
//            ?: throw IllegalStateException("Update before initialized")
            ?: return

        val updated = updater(initializedState)

        if (state != updated) {
            state = updated
            observer?.onReportState(updated/*, false*/)
            scheduleRefresh()
        }
    }


    fun update(state: ReportState) {
        if (this.state != state) {
            this.state = state
            observer?.onReportState(state/*, false*/)
            scheduleRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var refreshPending: Boolean = false
    private var previousRunning: Boolean = false
    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        refreshPending = false
        async {
            refresh()
            scheduleRefresh()
        }
    }, debounceMillis)


    private fun scheduleRefresh() {
//        val running = state?.run?.logicStatus?.active != null
        val running = state?.clientLogicState?.logicStatus?.active != null
//        console.log("^^^^ scheduleRefresh: $running - $refreshPending")

        if (refreshPending) {
            return
        }
//        println("scheduleRefresh - ${state().output.outputInfo}")

        if (running) {
            refreshPending = true
            refreshDebounce.apply()
        }
        else if (previousRunning) {
//            println("ReportStore - previousRunning")
            cancelRefresh()
            async {
                output.lookupOutputWithFallback()
                run.lookupProgressOfflineAsync()
                previewFiltered.lookupSummaryWithFallbackAsync()
            }
//            output.lookupOutputWithFallbackAsync()
//            run.lookupProgressOfflineAsync()
//            previewFiltered.lookupSummaryWithFallbackAsync()
        }
        previousRunning = running
    }


    private fun cancelRefresh() {
        refreshDebounce.cancel()
        refreshPending = false
    }
}