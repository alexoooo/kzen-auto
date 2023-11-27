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
import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation


class SequenceStore: ClientStateGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        const val debounceMillis = 1_500
        const val debounceMillis = 2_500
//        const val debounceMillis = 5_000

        private val allChangeTypes = ChangeType.entries.toSet()
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onSequenceState(sequenceState: SequenceState, changes: Set<ChangeType>)
    }

    enum class ChangeType {
        Notation,
        Progress,
        Validation,
        Error
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var mounted = false
    private var state: SequenceState? = null

    private var previousLogicTime: Instant = Instant.DISTANT_PAST
    private var previousDocumentNotation: DocumentNotation = DocumentNotation.empty

    val progressStore = SequenceProgressStore(this)
    val validationStore = SequenceValidationStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)

        state?.let {
            observer.onSequenceState(it, allChangeTypes)
        }
    }


    fun unobserve(observer: Observer) {
        val removed = observers.remove(observer)
        check(removed) { "Not found: $observer" }
    }


    private fun publish(nextState: SequenceState, changes: Set<ChangeType>) {
        for (observer in observers) {
            observer.onSequenceState(nextState, changes)
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

        val previousState = state
        val nextState = when {
            previousState == null || mainLocation != previousState.mainLocation -> {
                val sequenceTree = SequenceTree.read(documentNotation)
                SequenceState(
                    mainLocation,
                    documentNotation,
                    sequenceTree)
            }

            documentNotation != previousState.documentNotation -> {
                val sequenceTree = SequenceTree.read(documentNotation)
                if (previousState.sequenceTree == sequenceTree) {
                    previousState.copy(
                        documentNotation = documentNotation)
                }
                else {
                    previousState.copy(
                        documentNotation = documentNotation,
                        sequenceTree = sequenceTree)
                }
            }

            else ->
                previousState
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        updateIfChanged(nextState)

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
        updateIfChanged(updated)
    }


    fun updateIfChanged(nextState: SequenceState) {
        if (state == nextState) {
            return
        }

        val changes = detectChanges(nextState)
        state = nextState
        publish(nextState, changes)
    }


    private fun detectChanges(nextState: SequenceState): Set<ChangeType> {
        val changes = mutableSetOf<ChangeType>()

        if (state?.mainLocation != nextState.mainLocation ||
                state?.documentNotation != nextState.documentNotation
        ) {
            changes.add(ChangeType.Notation)
        }

        if (state?.progress != nextState.progress) {
            changes.add(ChangeType.Progress)
        }

        if (state?.validationState != nextState.validationState) {
            changes.add(ChangeType.Validation)
        }

        if (state?.globalError != nextState.globalError) {
            changes.add(ChangeType.Error)
        }

        return allChangeTypes
    }


    fun updateValidation(updater: (SequenceValidationState) -> SequenceValidationState) {
        update { state -> state
            .withValidation(updater)
        }
    }
}