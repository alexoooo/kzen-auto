package tech.kzen.auto.client.objects.document.sequence.model

import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import tech.kzen.auto.client.objects.document.sequence.progress.SequenceProgressStore
import tech.kzen.auto.client.objects.document.sequence.valid.SequenceValidationState
import tech.kzen.auto.client.objects.document.sequence.valid.SequenceValidationStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation


class SequenceStore: ClientStateGlobal.Observer {
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
    private val observers = mutableSetOf<Observer>()
    private var mounted = false
    private var state: SequenceState? = null

    private var previousLogicTime: Instant = Instant.DISTANT_PAST
    private var previousDocumentNotation: DocumentNotation = DocumentNotation.empty

//    private var refreshPending: Boolean = false
//    private var previousRunning: Boolean = false
//    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
//        refreshPending = false
//        async {
//            refresh()
//            scheduleRefresh()
//        }
//    }, debounceMillis)


    val progressStore = SequenceProgressStore(this)
    val validationStore = SequenceValidationStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)

        state?.let {
            observer.onSequenceState(it)
        }
    }


    fun unobserve(observer: Observer) {
        val removed = observers.remove(observer)
        check(removed) { "Not found: $observer" }
    }


    private fun publish(nextState: SequenceState) {
        for (observer in observers) {
            observer.onSequenceState(nextState)
        }
    }


    fun didMount() {
        mounted = true
        async {
            ClientContext.clientStateGlobal.observe(this)
        }
    }


    fun willUnmount() {
        mounted = false
        state = null
        ClientContext.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        if (! mounted) {
            return
        }

        val mainLocation = SequenceState.tryMainLocation(clientState)
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[mainLocation.documentPath]
            ?: return

//        val mainDefinition = mainDefinition(clientState, mainLocation)

        val previousState = state
        val nextState = when {
            previousState == null || mainLocation != previousState.mainLocation ->
                SequenceState(
                    mainLocation,
//                    mainDefinition
                )

            else ->
                previousState
//                previousState.copy(mainDefinition = mainDefinition)
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        if (state != nextState) {
            state = nextState
            publish(nextState)
        }

        if (initial) {
            refreshProgressAsync()
            refreshValidationAsync()
        }
        else {
            val logicTime: Instant = clientState.clientLogicState.logicStatus?.time ?: Instant.DISTANT_PAST
            if (previousLogicTime != logicTime) {
                previousLogicTime = logicTime
                refreshProgressAsync()
            }

            if (previousDocumentNotation != documentNotation) {
                refreshValidationAsync()
            }
        }
    }


//    private fun mainDefinition(clientState: ClientState, mainLocation: ObjectLocation): ObjectDefinition {
//        val mainDefinition = clientState
//            .graphDefinitionAttempt
//            .objectDefinitions[mainLocation]
//
//        check(mainDefinition != null) {
//            "Sequence definition missing: $mainLocation - ${clientState.graphDefinitionAttempt.failures[mainLocation]}"
//        }
//
//        return mainDefinition
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun refreshProgressAsync() {
        async {
            delay(10)
            if (state == null) {
                return@async
            }
            progressStore.refresh()
        }
    }


    private fun refreshValidationAsync() {
        async {
            delay(10)
            if (state == null) {
                return@async
            }
            validationStore.refresh()
        }
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
            publish(updated)
//            scheduleRefresh()
        }
    }


    fun update(nextState: SequenceState) {
        if (state != nextState) {
            state = nextState
            publish(nextState)
//            scheduleRefresh()
        }
    }


    fun updateValidation(updater: (SequenceValidationState) -> SequenceValidationState) {
        update { state -> state
            .withValidation(updater)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun scheduleRefresh() {
//        val running = state?.run?.logicStatus?.active != null
//
//        if (refreshPending) {
//            return
//        }
//
//        if (running) {
//            refreshPending = true
//            refreshDebounce.apply()
//        }
//        else if (previousRunning) {
//            cancelRefresh()
////            output.lookupOutputWithFallbackAsync()
////            run.lookupProgressOfflineAsync()
////            previewFiltered.lookupSummaryWithFallbackAsync()
//        }
//        previousRunning = running
//    }
//
//
//    private fun cancelRefresh() {
//        refreshDebounce.cancel()
//        refreshPending = false
//    }
}