package tech.kzen.auto.client.objects.document.script.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressStore
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationState
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import kotlin.time.Instant


class ScriptStore: ClientStateGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val allChangeTypes = ChangeType.entries.toSet()
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onScriptState(scriptState: ScriptState, changes: Set<ChangeType>)
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
    private var state: ScriptState? = null

    private var previousLogicTime: Instant = Instant.DISTANT_PAST
    private var previousDocumentNotation: DocumentNotation = DocumentNotation.empty

    val progressStore = ScriptProgressStore(this)
    val validationStore = ScriptValidationStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)

        state?.let {
            observer.onScriptState(it, allChangeTypes)
        }
    }


    fun unobserve(observer: Observer) {
        val removed = observers.remove(observer)
        check(removed) { "Not found: $observer" }
    }


    private fun publish(nextState: ScriptState, changes: Set<ChangeType>) {
        for (observer in observers) {
            observer.onScriptState(nextState, changes)
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
        if (!mounted) {
            return
        }

        val mainLocation = ScriptState.tryMainLocation(clientState)
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[mainLocation.documentPath]
            ?: return

        val previousState = state
        val nextState = when {
            previousState == null || mainLocation != previousState.mainLocation -> {
                val scriptTree = ScriptTree.read(
                    mainLocation.documentPath, clientState.graphDefinitionAttempt.successful())
                ScriptState(
                    mainLocation,
                    documentNotation,
                    scriptTree)
            }

            documentNotation != previousState.documentNotation -> {
                val scriptTree = ScriptTree.read(
                    mainLocation.documentPath, clientState.graphDefinitionAttempt.successful())
                if (previousState.scriptTree == scriptTree) {
                    previousState.copy(
                        documentNotation = documentNotation)
                }
                else {
                    previousState.copy(
                        documentNotation = documentNotation,
                        scriptTree = scriptTree)
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
    fun state(): ScriptState {
        return state
            ?: throw IllegalStateException("Get state before initialized")
    }


    fun mainLocation(): ObjectLocation {
        return state().mainLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun update(updater: (ScriptState) -> ScriptState) {
        val initializedState = state
            ?: return

        val updated = updater(initializedState)
        updateIfChanged(updated)
    }


    fun updateIfChanged(nextState: ScriptState) {
        if (state == nextState) {
            return
        }

        val changes = detectChanges(nextState)
        state = nextState
        publish(nextState, changes)
    }


    private fun detectChanges(nextState: ScriptState): Set<ChangeType> {
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


    fun updateValidation(updater: (ScriptValidationState) -> ScriptValidationState) {
        update { state -> state
            .withValidation(updater)
        }
    }
}