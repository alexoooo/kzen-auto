package tech.kzen.auto.client.objects.document.script.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.common.raw.*
import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressStore
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationState
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationStore
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant


class ScriptStore(
    val clientStateGlobal: ClientStateGlobal,
    val restClient: ClientRestApi,
    override val notationParser: NotationParser,
    override val mirroredGraphStore: MirroredGraphStore,
    val objectStableMapper: ObjectStableMapper
): ClientStateGlobal.Observer, DocumentRawHost {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // NB: yields the event loop so cascading onClientState → updateIfChanged → onScriptState
        //     settles before the network refresh kicks in.
        private val refreshYieldMillis = 10.milliseconds
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onScriptState(scriptState: ScriptState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var mounted = false
    private var state: ScriptState? = null

    private var previousLogicTime: Instant = Instant.DISTANT_PAST
    private var previousDocumentNotation: DocumentNotation = DocumentNotation.empty

    val progressStore = ScriptProgressStore(this)
    val validationStore = ScriptValidationStore(this)
    val stepStore = ScriptStepStore(this)
    val raw = DocumentRawStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)

        state?.let {
            observer.onScriptState(it)
        }
    }


    fun unobserve(observer: Observer) {
        val removed = observers.remove(observer)
        check(removed) { "Not found: $observer" }
    }


    private fun publish(nextState: ScriptState) {
        for (observer in observers) {
            observer.onScriptState(nextState)
        }
    }


    fun didMount() {
        mounted = true
        async {
            clientStateGlobal.observe(this)
        }
    }


    fun willUnmount() {
        mounted = false
        state = null
        clientStateGlobal.unobserve(this)
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
                ScriptState.initial(
                    mainLocation,
                    documentNotation,
                    scriptTree,
                    notationParser,
                    previousState?.viewMode ?: DocumentViewMode.View)
            }

            documentNotation != previousState.documentNotation -> {
                val scriptTree = ScriptTree.read(
                    mainLocation.documentPath, clientState.graphDefinitionAttempt.successful())
                previousState.withDocumentNotation(documentNotation, scriptTree, notationParser)
            }

            else ->
                previousState
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        updateIfChanged(nextState)

        val logicTime: Instant = clientState.clientLogicState.logicStatus?.time ?: Instant.DISTANT_PAST

        if (initial) {
            // Seed the change-detection baselines so the first subsequent onClientState doesn't
            // re-fire a refresh that this initial load already performed.
            previousLogicTime = logicTime
            previousDocumentNotation = documentNotation

            refreshProgressAsync()
            refreshValidationAsync()
        }
        else {
            val logicTimeChanged = previousLogicTime != logicTime
            val documentNotationChanged = previousDocumentNotation != documentNotation

            if (logicTimeChanged) {
                previousLogicTime = logicTime
            }
            if (documentNotationChanged) {
                previousDocumentNotation = documentNotation
            }

            // The trace snapshot is keyed by ObjectStableId and translated locally via the client
            // mapper, so it survives document edits (rename / shift / insert) without a re-fetch —
            // only a new run (logic time change) warrants pulling fresh progress.
            if (logicTimeChanged) {
                refreshProgressAsync()
            }

            if (documentNotationChanged) {
                refreshValidationAsync()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun refreshProgressAsync() {
        async {
            delay(refreshYieldMillis)
            if (state == null) {
                return@async
            }
            progressStore.refresh()
        }
    }


    private fun refreshValidationAsync() {
        async {
            delay(refreshYieldMillis)
            if (state == null) {
                return@async
            }
            validationStore.refresh()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): ScriptState {
        return checkNotNull(state) { "Get state before initialized" }
    }


    // Non-throwing snapshot read (mirrors ClientStateGlobal.current()), for consumers that may
    // render before the store is initialized or during teardown.
    // TODO: is this actually necessary, or just hypothetical benefit (YAGNI)?
    fun stateOrNull(): ScriptState? {
        return state
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

        state = nextState
        publish(nextState)
    }


    fun updateValidation(updater: (ScriptValidationState) -> ScriptValidationState) {
        update { state -> state
            .withValidation(updater)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setViewMode(viewMode: DocumentViewMode) {
        update { it.withViewMode(viewMode) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun rawSnapshot(): DocumentRawSnapshot? {
        val snapshot = state
            ?: return null
        return DocumentRawSnapshot(snapshot.mainLocation.documentPath, snapshot.raw, snapshot.editorModified)
    }


    override fun updateRaw(updater: (DocumentRawState) -> DocumentRawState) {
        update { it.withRaw(notationParser, updater) }
    }
}