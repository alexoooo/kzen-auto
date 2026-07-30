package tech.kzen.auto.client.objects.document.report.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaStore
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.objects.document.report.preview.model.ReportPreviewStore
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunStore
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.DefinitionErrors
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import kotlin.time.Duration.Companion.milliseconds


class ReportStore(
    val clientStateGlobal: ClientStateGlobal,
    val mirroredGraphStore: MirroredGraphStore,
    val restClient: ClientRestApi
): ClientStateGlobal.DocumentScopedObserver {
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

    private var previousLogicVersion: String = ClientLogicState.noTraceVersion

    // true while the report's `main` has no definition, so the recovery pass knows the notationError is ours
    private var definitionBlocked = false

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
            clientStateGlobal.observe(this)
        }
    }


    fun willUnmount() {
        observer = null
        mounted = false
        state = null

        clientStateGlobal.unobserve(this)
        cancelRefresh()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        if (!mounted) {
            return
        }

        val reportMainLocation = ReportState.tryMainLocation(clientState)
            ?: return

        val reportMainDefinition = mainDefinition(clientState, reportMainLocation)
        if (reportMainDefinition == null) {
            onDefinitionBlocked(clientState, reportMainLocation)
            return
        }

        // only the guard's own error may be wiped here - a sub-store's command-apply error is not ours to clear
        val recoveredFromDefinitionBlock = definitionBlocked
        definitionBlocked = false

        val previousState = state
        val nextState = when {
            previousState == null || reportMainLocation != previousState.mainLocation ->
                ReportState(
                    reportMainLocation,
                    reportMainDefinition,
                    clientState.clientLogicState)

            recoveredFromDefinitionBlock ->
                previousState.copy(
                    mainDefinition = reportMainDefinition,
                    clientLogicState = clientState.clientLogicState,
                    notationError = null
                )

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
//            println("Version: $previousLogicVersion | Active: ${clientState.clientLogicState.isActive()}")

            // Second disjunct translated as-is from the retired LogicStatus.time formulation, where the
            // sentinel was Instant.DISTANT_PAST: noTraceVersion means "no status seen yet", exactly as
            // DISTANT_PAST did (both hold iff logicStatus == null). Its effect — re-scheduling on every
            // publish once a status has been seen and the run is inactive — is pre-existing; preserved
            // deliberately rather than "fixed" here.
            val logicVersion: String = clientState.clientLogicState.traceVersion()
            if (previousLogicVersion != logicVersion ||
                previousLogicVersion != ClientLogicState.noTraceVersion && !clientState.clientLogicState.isActive()
            ) {
//                println("Scheduling $logicVersion")
                previousLogicVersion = logicVersion
                scheduleRefresh()
            }
        }
    }


    private fun mainDefinition(clientState: ClientState, mainLocation: ObjectLocation): ObjectDefinition? {
        return clientState
            .graphDefinitionAttempt
            .objectDefinitions[mainLocation]
    }


    // The document is a report in notation but its `main` failed to define. Never throw here: ClientStateGlobal's
    // fan-out turns any observer throw into a modal alert (and the initial replay isn't even wrapped). Surface the
    // origin instead - StageController's panel names object and attribute above the body, and a report that was
    // already open freezes with the reason pinned to it.
    private fun onDefinitionBlocked(clientState: ClientState, mainLocation: ObjectLocation) {
        definitionBlocked = true

        val previousState = state
            ?: return

        if (previousState.mainLocation != mainLocation) {
            return
        }

        val blocker = DefinitionErrors.runBlocker(clientState.graphDefinitionAttempt, mainLocation)
        val nextState = previousState.withNotationError(blocker ?: "Report failed to define")

        if (state != nextState) {
            state = nextState
            observer?.onReportState(nextState)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun initAsync() {
        async {
            delay(10.milliseconds)
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