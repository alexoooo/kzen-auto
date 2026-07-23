package tech.kzen.auto.client.objects.document.script.model

import kotlinx.coroutines.delay
import tech.kzen.auto.client.objects.document.common.raw.*
import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressStore
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationState
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationStore
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import kotlin.time.Duration.Companion.milliseconds


class ScriptStore(
    val clientStateGlobal: ClientStateGlobal,
    private val logicValidationGlobal: LogicValidationGlobal,
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

    private var previousLogicVersion: String = ClientLogicState.noTraceVersion
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

        val logicVersion: String = clientState.clientLogicState.traceVersion()

        if (initial) {
            // Seed the change-detection baselines so the first subsequent onClientState doesn't
            // re-fire a refresh that this initial load already performed.
            previousLogicVersion = logicVersion
            previousDocumentNotation = documentNotation

            refreshProgressAsync()
            refreshValidationAsync()
        }
        else {
            val logicVersionChanged = previousLogicVersion != logicVersion
            val documentNotationChanged = previousDocumentNotation != documentNotation

            if (logicVersionChanged) {
                previousLogicVersion = logicVersion
            }
            if (documentNotationChanged) {
                previousDocumentNotation = documentNotation
            }

            // The trace snapshot is keyed by ObjectStableId and translated locally via the client
            // mapper, so it survives document edits (rename / shift / insert) without a re-fetch —
            // only the run actually advancing (trace version change) warrants pulling fresh progress.
            if (logicVersionChanged) {
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


    // Guards the validation channel against overlapping refreshes: each arm bumps the epoch and only the
    // latest-armed refresh runs and settles. An earlier refresh completing mid-flight of a newer one would
    // drop the busy indicator early and publish a stale reason; and since one store instance serves successive
    // same-archetype documents (see dependencyAnalysis), a superseded async could otherwise settle the OLD
    // document's channel with a reason computed from the NEW document's state.
    private var validationEpoch = 0


    private fun refreshValidationAsync() {
        // Only ever called after updateIfChanged(nextState) set `state`, so mainLocation is available. Mark the
        // validation channel in-flight SYNCHRONOUSLY (before the yield below) so the run cluster's busy spinner
        // lights up with no flicker gap ahead of the actual refresh, carrying the LAST-KNOWN reason so Run
        // doesn't flicker-enable mid-revalidation.
        val documentPath = state?.mainLocation?.documentPath
            ?: return
        val epoch = ++validationEpoch
        logicValidationGlobal.validation(documentPath, inFlight = true, invalidReason = currentValidationReason())

        async {
            delay(refreshYieldMillis)
            if (epoch != validationEpoch || state == null) {
                return@async
            }
            validationStore.refresh()

            if (epoch != validationEpoch || state == null) {
                // Superseded while the fetch was in flight (or the store unmounted, dropping the result) — the
                // newest arm owns the settle.
                return@async
            }

            // Settle the channel with the freshly-computed reason (null when the fetch failed → Run enabled on
            // unknown validity, matching "null = valid/unknown"; the global error banner carries the failure).
            logicValidationGlobal.validation(
                documentPath, inFlight = false, invalidReason = currentValidationReason())
        }
    }


    // The first step-validation error across the current (last-fetched) ScriptValidation — the flavour-agnostic
    // "invalid" predicate (any StepValidation.errorMessage != null). Null when valid or not yet fetched.
    private fun currentValidationReason(): String? {
        return state?.validationState?.scriptValidation?.stepValidations?.values
            ?.firstNotNullOfOrNull { it.errorMessage }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): ScriptState {
        return checkNotNull(state) { "Get state before initialized" }
    }


    // Non-throwing snapshot read (mirrors ClientStateGlobal.current() and CustomStore.stateOrNull), for
    // consumers reading OUTSIDE the observer flow, where the throwing state() would be unsafe: the reader can
    // run before the store is initialized or after willUnmount() nulls it. Concretely
    // StepImageFullscreen.scriptState(), reached from render() and from navigate() — the latter off a
    // window-keydown listener, so outside React's lifecycle guarantees entirely.
    fun stateOrNull(): ScriptState? {
        return state
    }


    fun mainLocation(): ObjectLocation {
        return state().mainLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Single-entry memo for ScriptDependencyAnalysis.analyze, shared by every consumer in this document's
    // subtree (branch gutters, dependency overlay, signature editor, move-to arrow) — the analysis re-lexes
    // every value scalar in the document, and used to run once per consumer per publish.
    // Self-keyed on the GraphDefinitionAttempt REFERENCE (replaced only on notation events — logic-status
    // publishes reuse it, so the run hot path always hits) plus the documentPath (ScriptController isn't
    // remounted on a same-archetype document switch, so one store instance can serve successive documents).
    // NB: never key on successful() — it allocates a fresh GraphDefinition per call.
    private var dependencyAnalysisKeyAttempt: GraphDefinitionAttempt? = null
    private var dependencyAnalysisKeyPath: DocumentPath? = null
    private var dependencyAnalysisCached: ScriptDependencyAnalysis? = null


    fun dependencyAnalysis(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        documentPath: DocumentPath
    ): ScriptDependencyAnalysis {
        val cached = dependencyAnalysisCached
        if (cached != null &&
                graphDefinitionAttempt === dependencyAnalysisKeyAttempt &&
                documentPath == dependencyAnalysisKeyPath
        ) {
            return cached
        }

        val computed = ScriptDependencyAnalysis.analyze(
            graphDefinitionAttempt.successful(), documentPath)

        dependencyAnalysisKeyAttempt = graphDefinitionAttempt
        dependencyAnalysisKeyPath = documentPath
        dependencyAnalysisCached = computed
        return computed
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