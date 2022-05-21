package tech.kzen.auto.client.objects.document.sequence.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.common.run.ExecutionRunStore
import tech.kzen.auto.client.objects.document.sequence.progress.SequenceProgressStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation


class SequenceStore: SessionGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        const val debounceMillis = 1_500
        const val debounceMillis = 2_500
//        const val debounceMillis = 5_000
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onSequenceState(sequenceState: SequenceState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var observer: Observer? = null
    private var mounted = false
    private var state: SequenceState? = null


    private var refreshPending: Boolean = false
    private var previousRunning: Boolean = false
    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        refreshPending = false
        async {
            refresh()
            scheduleRefresh()
        }
    }, debounceMillis)


//    val input = ReportInputStore(this)
//    val formula = ReportFormulaStore(this)
//    val filter = ReportFilterStore(this)
//    val analysis = ReportAnalysisStore(this)
//    val previewFiltered = ReportPreviewStore(this)
//    val output = ReportOutputStore(this)
    val run = ExecutionRunStore(
        { state?.run!! },
        { state?.mainLocation!! },
        {
            state = state!!.copy(
                run = it(state!!.run))
        },
        {
//            console.log("refresh - $it")
        }
    )


    private val progressStore = SequenceProgressStore(this)



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

        val reportMainLocation = SequenceState.tryMainLocation(clientState)
            ?: return

        val reportMainDefinition = mainDefinition(clientState, reportMainLocation)

        val previousState = state
        val nextState = when {
            previousState == null || reportMainLocation != previousState.mainLocation ->
                SequenceState(
                    reportMainLocation,
                    reportMainDefinition)

            else ->
                previousState.copy(mainDefinition = reportMainDefinition)
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        if (state != nextState) {
            state = nextState
            observer?.onSequenceState(nextState)
        }

        if (initial) {
            cancelRefresh()
            initAsync()
        }
    }


    private fun mainDefinition(clientState: SessionState, mainLocation: ObjectLocation): ObjectDefinition {
        val mainDefinition = clientState
            .graphDefinitionAttempt
            .objectDefinitions[mainLocation]

        check(mainDefinition != null) {
            "Sequence definition missing: $mainLocation - ${clientState.graphDefinitionAttempt.failures[mainLocation]}"
        }

        return mainDefinition
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun initAsync() {
        async {
            delay(10)
            if (state == null) {
                return@async
            }

//            output.init()
//            run.init()
//            input.init()
//            formula.validateAsync()
//            previewFiltered.init()

            progressStore.init()

            run.open()
        }
    }


    private suspend fun refresh() {
        run.refresh()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): SequenceState {
        return state
            ?: throw IllegalStateException("Get state before initialized")
    }


    fun mainLocation(): ObjectLocation {
        return state().mainLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun update(updater: (SequenceState) -> SequenceState) {
        val initializedState = state
            ?: return

        val updated = updater(initializedState)

        if (state != updated) {
            state = updated
            observer?.onSequenceState(updated)
            scheduleRefresh()
        }
    }


    fun update(state: SequenceState) {
        if (this.state != state) {
            this.state = state
            observer?.onSequenceState(state)
            scheduleRefresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scheduleRefresh() {
        val running = state?.run?.logicStatus?.active != null

        if (refreshPending) {
            return
        }

        if (running) {
            refreshPending = true
            refreshDebounce.apply()
        }
        else if (previousRunning) {
            println("ReportStore - previousRunning")
            cancelRefresh()
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