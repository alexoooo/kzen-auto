package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.bridge.InsertionKey
import tech.kzen.auto.client.objects.document.common.dragdrop.dropZoneRegion
import tech.kzen.auto.client.objects.document.common.signature.LogicSignatureEditor
import tech.kzen.auto.client.objects.document.common.signature.ResultSignatureEditor
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayManager
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayPropsCommon
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface JobControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var logicValidationGlobal: LogicValidationGlobal
    var restClient: ClientRestApi
    var objectStableMapper: ObjectStableMapper
    var mirroredGraphStore: MirroredGraphStore
    var workerDisplayManager: WorkerDisplayManager.Wrapper
}


external interface JobControllerState: State {
    var clientState: ClientState?

    // Per-Worker live progress (status + counts + preview teaser), polled from the logic trace store after
    // each run-status change. Null until the first fetch. Threaded to each card via WorkerDisplayPropsCommon; a
    // preview card layers its own on-demand larger slice on top (see PreviewWorkerDisplay).
    var workerProgress: Map<ObjectLocation, JobWorkerProgress>?

    // The latest run's trace snapshot (the same fetch [workerProgress] is projected from), threaded to the
    // signature editor — it reads each declared parameter's emitted run value off it by stable id. Null until
    // a run exists. Value-gated so the reference stays ===-stable across identical polls.
    var traceSnapshot: LogicTraceSnapshot?

    // Per-Worker server-side validation (the static payload-type walk: inferred payload types + expression
    // compile errors), fetched from the JobValidator detached action on notation change. Null until the first
    // fetch. Threaded to each card via WorkerDisplayPropsCommon (type chip + error icon).
    var workerValidations: JobValidation?

    // Workers in document order — the stage list. Value-gated in onClientState so the instances stay
    // ===-stable across drag / progress re-renders, letting each JobObjectSlot bail out.
    var workerLocations: List<ObjectLocation>?

    // The order-driven channels, keyed by UPSTREAM Worker location: a gold pipe renders in the gap below that
    // Worker. Derived (purely) from Worker order + typed ports — the same rule the server's synthesis uses.
    var connectionsByUpstream: Map<ObjectLocation, JobChannelDerivation.Connection>?

    // Ribbon insert-mode: while true, a "+" insertion point shows in every gap so the user picks where the
    // selected archetype lands (mirrors ScriptBranchDisplay's `creating`).
    var creating: Boolean

    // Active drag: the index in [workerLocations] being dragged, and the cursor's insertion index (0..size)
    // computed from card midpoints. Both null when no drag is in progress.
    var dragSourceIndex: Int?
    var dropInsertionIndex: Int?

    // Channels whose inline editor is expanded, keyed by upstream Worker reference
    // (connection.upstreamWorker.toReference().asString()). Null / absent = collapsed to the compact chevron.
    // Local UI toggle only — never round-trips through notation; a channel's customization (its persisted
    // config) is independent of whether its editor is currently open.
    var expandedChannels: Set<String>?
}


//---------------------------------------------------------------------------------------------------------------------
// The Job editor: a single document-order stage of Worker cards (white node cards with attribute editors, live
// status / counts, and — for a Preview worker — a live sample table), with the Channels connecting them drawn
// as gold pipes in the gaps between cards (JobChannelDisplay). Channels are auto-managed: the pipeline is derived
// from Worker order + typed ports (JobChannelDerivation) and synthesized at run time, so the saved notation
// keeps Worker ports blank and carries no Channel objects on the common path. Worker cards can be reordered by
// drag/drop and the ribbon insert drops at a chosen position; reordering re-forms the pipes. Run / Step / Pause
// come from the shared logic ribbon, exactly like Script / Flow. A graphical node-and-edge canvas is deferred
// (see kzen/plans/2026-07-25_job-improvements.md).
@Suppress("unused")
class JobController(
    props: JobControllerProps
):
    RPureComponent<JobControllerProps, JobControllerState>(props),
    ClientStateGlobal.DocumentScopedObserver,
    InsertionGlobal.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val dragHandleColor = Color("rgba(0, 0, 0, 0.45)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val ribbonController: RibbonController.Wrapper,
        private val workerDisplayManager: WorkerDisplayManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val logicValidationGlobal: LogicValidationGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    ribbonController.child(this) {}
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    JobController::class.react {
                        this.clientStateGlobal = this@Wrapper.clientStateGlobal
                        this.logicValidationGlobal = this@Wrapper.logicValidationGlobal
                        this.restClient = this@Wrapper.restClient
                        this.objectStableMapper = this@Wrapper.objectStableMapper
                        this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        this.workerDisplayManager = this@Wrapper.workerDisplayManager
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // The ribbon (header) publishes the selected insertion archetype on this per-document channel; the body
        // subscribes and inserts it. Reached via the shared DocumentBridge context (see RibbonController).
        installContextType(DocumentBridgeContext)
    }

    private fun insertion(): InsertionGlobal? =
        contextValue<DocumentBridge?>()?.channel(InsertionKey)


    //-----------------------------------------------------------------------------------------------------------------
    // Built once per instance, so they are ===-stable across renders and let each JobObjectSlot (RPureComponent)
    // bail when only the drag indicator changed. The slot threads its own index / location back in, so a single
    // shared reference serves every slot rather than a fresh closure per slot per render.
    private val onSlotDragStart: (Int) -> Unit = { index -> onDragStart(index) }
    private val onSlotDragEnd: () -> Unit = { onDragEnd() }

    // Stable so JobChannelDisplay (RPureComponent) bails on drag-hover re-renders; a clicked chevron toggles its
    // channel's inline editor open / closed (keyed by upstream Worker reference). Read the current set OUTSIDE
    // the setState lambda — wrap/React.kt's setState runs the lambda on an empty partial (write-only).
    private val onPipeToggle: (ObjectLocation) -> Unit = { upstreamWorker ->
        val key = upstreamWorker.toReference().asString()
        val current = state.expandedChannels ?: emptySet()
        val next = if (key in current) current - key else current + key
        setState {
            expandedChannels = next
        }
    }

    // Stable so each JobChannelDisplay (RPureComponent) keeps bailing out on drag-hover re-renders; the trash
    // button in an expanded channel removes its per-output override (and collapses the editor).
    private val onChannelClear: (ObjectLocation, AttributeName) -> Unit = { upstreamWorker, outputPort ->
        clearChannelConfig(upstreamWorker, outputPort)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val jobProgressStore by lazy {
        JobProgressStore(props.restClient, props.objectStableMapper)
    }

    // Refetch progress only when the document or the run status changes (mirrors FlowController); during a run
    // the status time advances on each logic-status poll, so this also drives the live progress refresh.
    private var lastFetchKey: String? = null


    private val jobValidationStore by lazy {
        JobValidationStore(props.restClient)
    }

    // Refetch validation only when this Job's own notation actually changed (the ScriptStore
    // documentNotationChanged split — progress polls and unrelated client-state publishes reuse the instance,
    // so a reference compare suffices). Same accepted limitation as Script: a callee / registry edit
    // refreshes on the next own-document change.
    private var lastValidationNotation: DocumentNotation? = null

    // Guards the validation channel against overlapping fetches: each arm bumps the epoch and only the
    // latest-armed fetch settles — an earlier fetch completing mid-flight of a newer one would drop the run
    // cluster's busy indicator early and publish a stale reason.
    private var validationEpoch = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun JobControllerState.init(props: JobControllerProps) {
        clientState = null
        workerProgress = null
        traceSnapshot = null
        workerValidations = null
        workerLocations = null
        connectionsByUpstream = null
        creating = false
        dragSourceIndex = null
        dropInsertionIndex = null
        expandedChannels = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        insertion()?.subscribe(this)
    }


    override fun componentWillUnmount() {
        insertion()?.unsubscribe(this)
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }

        updateStageModel(clientState)
        refreshProgressIfNeeded(clientState)
        refreshValidationIfNeeded(clientState)
    }


    // Recompute the Worker list + derived pipes and store them ONLY when changed, so the held List /
    // ObjectLocation / Connection instances stay ===-stable across drag-hover and progress-poll re-renders —
    // that stability is what lets each JobObjectSlot and JobChannelDisplay bail.
    private fun updateStageModel(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val graphStructure = clientState.graphStructure()
        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return
        if (!JobConventions.isJob(documentNotation)) {
            return
        }

        val workers = workerPaths(documentNotation).map { ObjectLocation(documentPath, it) }
        if (workers != state.workerLocations) {
            setState {
                workerLocations = workers
            }
        }

        val connections = JobChannelDerivation.derive(graphStructure, documentPath)
            .connections
            .associateBy { it.upstreamWorker }
        if (connections != state.connectionsByUpstream) {
            setState {
                connectionsByUpstream = connections
            }
        }
    }


    private fun refreshProgressIfNeeded(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (!JobConventions.isJob(documentNotation)) {
            return
        }

        // Key on the run's trace version so the live progress refreshes exactly when the run actually advanced
        // (not on every status poll — see ClientLogicState.traceVersion), and on the document path so opening
        // a Job loads its last run's final progress.
        val logicTraceVersion = clientState.clientLogicState.traceVersion()
        val fetchKey = "${documentPath.asString()}|$logicTraceVersion"
        if (fetchKey == lastFetchKey) {
            return
        }
        lastFetchKey = fetchKey

        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val workerLocations = workerPaths(documentNotation)
            .map { ObjectLocation(documentPath, it) }

        async {
            val runProgress = jobProgressStore.fetchRunProgress(mainLocation, workerLocations)
            // Value-equality gates (data class + map): skip setState when nothing changed so RPureComponent
            // bails out instead of re-rendering on every (identical) poll.
            val progress = runProgress?.workerProgress ?: mapOf()
            if (progress != state.workerProgress) {
                setState {
                    workerProgress = progress
                }
            }
            val snapshot = runProgress?.traceSnapshot
            if (snapshot != state.traceSnapshot) {
                setState {
                    traceSnapshot = snapshot
                }
            }
        }
    }


    private fun refreshValidationIfNeeded(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (!JobConventions.isJob(documentNotation)) {
            return
        }

        if (documentNotation === lastValidationNotation) {
            return
        }
        lastValidationNotation = documentNotation

        // Mark the run cluster's validation channel in-flight synchronously (before the async fetch), carrying the
        // last-known reason so Run doesn't flicker-enable mid-revalidation.
        val epoch = ++validationEpoch
        props.logicValidationGlobal.validation(
            documentPath, inFlight = true, errors = jobValidationErrors(state.workerValidations, documentPath))

        // The arm condition IS this notation's reference change, so the arm-time digest is the current local
        // digest until a newer arm supersedes this epoch (which the lambda below signals by returning null).
        val expectedDigest = documentNotation.digest()

        async {
            val validation = jobValidationStore.fetch(documentPath) {
                if (epoch != validationEpoch) null else expectedDigest
            }
            // Value-equality gate (data class + map): skip setState when nothing changed. A failed fetch
            // (null) leaves the previous validation standing rather than flashing the chips away.
            if (validation != null && validation != state.workerValidations) {
                setState {
                    workerValidations = validation
                }
            }

            if (epoch != validationEpoch) {
                // Superseded while the fetch was in flight — the newest arm owns the settle.
                return@async
            }

            // Settle the channel. On a failed fetch the previous validation still stands (reason unchanged);
            // otherwise the freshly-fetched one. `validation` is read directly (not through the async setState).
            val settled = validation ?: state.workerValidations
            props.logicValidationGlobal.validation(
                documentPath, inFlight = false, errors = jobValidationErrors(settled, documentPath))
        }
    }


    // Every Worker-validation error across the given JobValidation, each tied to its worker's ObjectLocation.
    // Empty when valid or not yet fetched; the run gate's "invalid" predicate falls out of this being non-empty.
    private fun jobValidationErrors(
        validation: JobValidation?,
        documentPath: DocumentPath
    ): List<LogicValidationGlobal.ValidationErrorLine> {
        val workerValidations = validation?.workerValidations
            ?: return emptyList()
        return workerValidations.mapNotNull { (objectPath, stepValidation) ->
            stepValidation.errorMessage?.let {
                LogicValidationGlobal.ValidationErrorLine(ObjectLocation(documentPath, objectPath), it)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Ribbon archetype selected: enter insert-mode so the user picks a position (the "+" gaps), rather than
    // inserting immediately. The selection stays in InsertionGlobal until a gap is clicked (onCreate).
    override fun onInsertionSelected(action: ObjectLocation) {
        setState {
            creating = true
        }
    }


    override fun onInsertionUnselected() {
        setState {
            creating = false
        }
    }


    // Insert the ribbon-selected archetype at the clicked gap: a Worker nests under `workers`, a (manually
    // created) Channel under `channels` — both at the chosen document position. getAndClearSelection also fires
    // onInsertionUnselected, ending insert-mode.
    private fun onCreate(gapIndex: Int) {
        val archetype = insertion()?.getAndClearSelection()
            ?: return

        async {
            insertArchetypeAt(archetype, gapIndex)
        }
    }


    private suspend fun insertArchetypeAt(archetype: ObjectLocation, gapIndex: Int) {
        val clientState = props.clientStateGlobal.current()
            ?: return
        val graphNotation = clientState.graphStructure().graphNotation

        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val documentNotation = graphNotation.documents[documentPath]
            ?: return
        if (!JobConventions.isJob(documentNotation)) {
            return
        }

        val attributePath =
            if (JobConventions.isChannelArchetype(graphNotation, archetype)) {
                JobConventions.channelsAttributePath
            }
            else {
                JobConventions.workersAttributePath
            }

        val namePrefix = graphNotation
            .firstAttribute(archetype, AutoConventions.titleAttributePath)
            ?.asString()
            ?: archetype.objectPath.name.value

        val existingNames = documentNotation.objects.notations.map.keys
            .map { it.name }
            .toSet()

        val newName = NextAvailableName
            .find(namePrefix, separator = " ") { ObjectName(it) !in existingNames }
            ?.let { ObjectName(it) }
            ?: AutoConventions.randomAnonymous()

        val newObjectLocation = ObjectLocation(
            documentPath,
            NotationConventions.mainObjectPath.nest(attributePath, newName))

        val insertIndex = insertionDocumentIndex(documentNotation, gapIndex)

        props.mirroredGraphStore.apply(AddObjectCommand.ofParent(
            newObjectLocation,
            PositionRelation.at(insertIndex),
            archetype.objectPath.name))
    }


    // The document index at which to insert so the new object lands at the requested Worker gap: before the
    // Worker currently occupying that slot, after the last Worker, or right after main when the stage is empty.
    private fun insertionDocumentIndex(documentNotation: DocumentNotation, gapIndex: Int): Int {
        val workers = state.workerLocations ?: listOf()
        return when {
            workers.isEmpty() ->
                documentNotation.indexOf(NotationConventions.mainObjectPath).value + 1

            gapIndex < workers.size ->
                documentNotation.indexOf(workers[gapIndex].objectPath).value

            else ->
                documentNotation.indexOf(workers.last().objectPath).value + 1
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onDragStart(sourceIndex: Int) {
        setState {
            dragSourceIndex = sourceIndex
        }
    }


    // The whole stage is one drop zone: claim a single insertion index from the cursor's Y against the card
    // midpoints, and re-render only when it changed (throttles the hover re-renders). Bound to both dragenter
    // and dragover so the index is claimed even if dragover is slow to re-fire.
    private fun onStageDragOver(event: DragEvent<HTMLDivElement>) {
        if (state.dragSourceIndex == null) {
            return
        }
        event.preventDefault()

        val workers = state.workerLocations
            ?: return
        val insertionIndex = computeInsertionFromCursor(event.clientY, workers)
        if (state.dropInsertionIndex != insertionIndex) {
            setState {
                dropInsertionIndex = insertionIndex
            }
        }
    }


    private fun onStageDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()

        val source = state.dragSourceIndex
        val insertionIndex = state.dropInsertionIndex
        setState {
            dragSourceIndex = null
            dropInsertionIndex = null
        }

        if (source == null || insertionIndex == null) {
            return
        }
        performShift(source, insertionIndex)
    }


    private fun onDragEnd() {
        if (state.dragSourceIndex == null && state.dropInsertionIndex == null) {
            return
        }
        setState {
            dragSourceIndex = null
            dropInsertionIndex = null
        }
    }


    // Insertion index (0..size) = the number of card midpoints above the cursor; cards come from
    // JobCardRowRegistry, in document order. An unregistered card is skipped (shouldn't happen for a visible
    // one); an empty stage yields 0.
    private fun computeInsertionFromCursor(clientY: Double, workers: List<ObjectLocation>): Int {
        var index = 0
        for (workerLocation in workers) {
            val element = JobCardRowRegistry.get(workerLocation)
                ?: continue
            val rect = element.getBoundingClientRect()
            if (clientY < rect.top + rect.height / 2) {
                break
            }
            index++
        }
        return index
    }


    // Move the dragged Worker to the chosen position. insertionIndex is in the pre-removal list, so dropping at
    // the dragged Worker's own two edges is a no-op; otherwise account for the card leaving its slot when it
    // sits above the target. The target document index is resolved against the document with the dragged object
    // removed (mirrors ScriptBranchDisplay, simplified for flat single objects).
    private fun performShift(source: Int, insertionIndex: Int) {
        if (insertionIndex == source || insertionIndex == source + 1) {
            return
        }

        val clientState = props.clientStateGlobal.current()
            ?: return
        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return
        val workers = state.workerLocations
            ?: return
        val draggedLocation = workers.getOrNull(source)
            ?: return
        val draggedPath = draggedLocation.objectPath

        val newIndex = if (insertionIndex > source) insertionIndex - 1 else insertionIndex
        val remainingPaths = documentNotation.objects.notations.map.keys.filter { it != draggedPath }
        val remainingWorkers = workers.filterIndexed { i, _ -> i != source }

        val anchor = remainingWorkers.getOrNull(newIndex)?.objectPath
        val targetDocumentIndex =
            if (anchor != null) {
                remainingPaths.indexOf(anchor)
            }
            else {
                val last = remainingWorkers.lastOrNull()?.objectPath
                if (last != null) remainingPaths.indexOf(last) + 1 else remainingPaths.size
            }

        async {
            props.mirroredGraphStore.apply(ShiftObjectTreeCommand(
                draggedLocation,
                PositionRelation.at(targetDocumentIndex)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun workerPaths(documentNotation: DocumentNotation): List<ObjectPath> {
        return documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val graphNotation = clientState.graphStructure().graphNotation
        val documentNotation = graphNotation.documents[documentPath]
            ?: return

        if (!JobConventions.isJob(documentNotation)) {
            return
        }

        val main = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val workers = state.workerLocations ?: listOf()
        val connections = state.connectionsByUpstream ?: mapOf()
        val active = clientState.clientLogicState.isActive()

        div {
            css {
                margin = Margin(2.em, 2.em, 2.em, 2.em)
                // Positioning context for the floating Channel-defaults panel (top-right, like Script's controls).
                position = Position.relative
            }

            // The whole stage is one drop zone; the drop index is computed from the cursor Y (onStageDragOver).
            onDragEnter = { event -> onStageDragOver(event) }
            onDragOver = { event -> onStageDragOver(event) }
            onDrop = { event -> onStageDrop(event) }

            // Typed parameter declarations (Script parity): the SAME editor Script uses, over the shared
            // `parameters` branch of ParameterBinding objects — its "Parameters" control floats at the top-right
            // and the declared rows flow above the Worker stage. The Script-only dependency gutter degrades to
            // empty lanes here (no ScriptStore in the Job bridge).
            LogicSignatureEditor::class.react {
                objectLocation = main
                clientStateGlobal = props.clientStateGlobal
                mirroredGraphStore = props.mirroredGraphStore
                logicTraceSnapshot = state.traceSnapshot
                objectStableMapper = props.objectStableMapper
            }

            // The declared result signature (Script parity): the SAME editor Script uses, over the `results`
            // map on `main` — the type each ResultSink Worker's yielded component is declared as.
            ResultSignatureEditor::class.react {
                objectLocation = main
                clientStateGlobal = props.clientStateGlobal
                mirroredGraphStore = props.mirroredGraphStore
            }

            // Job-wide channel defaults (batchSize / capacity) applied to every auto-synthesized channel — floated
            // at the top-right, stacked beneath the Parameters and Result controls (the ResultSignatureEditor
            // stacking convention); rendered before the empty/populated split so it shows in both.
            JobChannelDefaults::class.react {
                mainLocation = main
                clientStateGlobal = props.clientStateGlobal
                mirroredGraphStore = props.mirroredGraphStore
            }

            if (workers.isEmpty()) {
                div {
                    css {
                        fontSize = 1.25.em
                        marginBottom = 1.em
                    }
                    +"Empty Job — add Workers from the ribbon above."
                }
                insertionGap(0, null, documentPath, graphNotation)
                return@div
            }

            insertionGap(0, null, documentPath, graphNotation)
            for ((index, workerLocation) in workers.withIndex()) {
                renderWorkerSlot(index, workerLocation, active)

                // The pipe (if any) for this Worker lives in the gap directly below it (upstream = this Worker).
                // The last Worker's gap is a plain trailing insert / drop gap.
                val connection =
                    if (index < workers.size - 1) connections[workerLocation]
                    else null
                insertionGap(index + 1, connection, documentPath, graphNotation)
            }
        }
    }


    private fun ChildrenBuilder.renderWorkerSlot(
        index: Int,
        workerLocation: ObjectLocation,
        active: Boolean
    ) {
        JobObjectSlot::class.react {
            key = Key(workerLocation.toReference().asString())

            this.objectLocation = workerLocation
            this.indexInParent = index

            this.progress = state.workerProgress?.get(workerLocation)
            this.validation = state.workerValidations?.workerValidations?.get(workerLocation.objectPath)
            this.active = active

            this.workerDisplayManager = props.workerDisplayManager

            this.isDragSource = state.dragSourceIndex == index
            this.handleColor = dragHandleColor

            this.onDragStart = onSlotDragStart
            this.onDragEnd = onSlotDragEnd
        }
    }


    // A gap between cards (index 0 above the first, index size after the last): renders the gold pipe for the
    // order-driven channel when there is one and the stage is idle; the drop indicator when it's the active
    // drop target; and a "+" insert button while in ribbon insert-mode. Height is reserved so toggling modes
    // never shifts the card layout.
    private fun ChildrenBuilder.insertionGap(
        gapIndex: Int,
        connection: JobChannelDerivation.Connection?,
        documentPath: DocumentPath,
        graphNotation: GraphNotation
    ) {
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)

        // Collapsed by default; clicking the chevron expands the channel's single inline editor (local UI
        // toggle, keyed by upstream Worker). A channel's persisted customization is independent of expansion —
        // when collapsed the chevron carries a cue instead (see `customized`).
        val channelKey = connection?.upstreamWorker?.toReference()?.asString()
        val expanded = channelKey != null && state.expandedChannels?.contains(channelKey) == true

        // Each knob's explicit override (Worker's own value, else null = inheriting). Drives the collapsed
        // caption (overridden knobs only), the bolder chevron, and the taller reserved gap.
        val batchSizeOverride = connection?.let {
            JobChannelDisplay.ownChannelValue(
                graphNotation, it.upstreamWorker, it.outputPort, JobConventions.batchSizeAttributeName) }
        val capacityOverride = connection?.let {
            JobChannelDisplay.ownChannelValue(
                graphNotation, it.upstreamWorker, it.outputPort, JobConventions.capacityAttributeName) }
        val customized = batchSizeOverride != null || capacityOverride != null

        div {
            css {
                position = Position.relative
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                maxWidth = 40.em

                // Reserved height depends ONLY on the gap's own content, never on insert-mode — so entering /
                // leaving insert-mode never reflows the cards (mirrors ScriptBranchDisplay). The expanded card
                // sizes its own gap; a collapsed channel uses minHeight (not a fixed height) so a customized
                // channel's caption + bottom margin add intrinsic height instead of being clipped; an empty gap
                // is a thin strip. The "+" appears as an absolute overlay (below), contributing no height.
                if (!expanded) {
                    when {
                        connection != null -> minHeight = if (customized) 2.6.em else 1.5.em
                        else -> height = 0.75.em
                    }
                }
            }

            if (isActiveDropGap(gapIndex)) {
                dropZoneRegion()
            }

            // The channel pipe stays mounted in every mode — the insert "+" is layered ON TOP (below),
            // never replaces it.
            if (connection != null) {
                JobChannelDisplay::class.react {
                    key = Key("channel:" + connection.upstreamWorker.toReference().asString())
                    upstreamName = connection.upstreamWorker.objectPath.name.value
                    downstreamName = connection.downstreamWorker.objectPath.name.value
                    upstreamWorker = connection.upstreamWorker
                    outputPort = connection.outputPort
                    this.batchSizeOverride = batchSizeOverride
                    this.capacityOverride = capacityOverride
                    batchSize = JobChannelDisplay.effectiveChannelValue(
                        graphNotation, connection.upstreamWorker, mainLocation,
                        connection.outputPort, JobConventions.batchSizeAttributeName, "1024")
                    capacity = JobChannelDisplay.effectiveChannelValue(
                        graphNotation, connection.upstreamWorker, mainLocation,
                        connection.outputPort, JobConventions.capacityAttributeName, "0")
                    batchSizeFallback = JobChannelDisplay.effectiveDefaultValue(
                        graphNotation, mainLocation, JobConventions.batchSizeAttributeName, "1024")
                    capacityFallback = JobChannelDisplay.effectiveDefaultValue(
                        graphNotation, mainLocation, JobConventions.capacityAttributeName, "0")
                    this.expanded = expanded
                    onToggle = onPipeToggle
                    onClear = onChannelClear
                    clientStateGlobal = props.clientStateGlobal
                    mirroredGraphStore = props.mirroredGraphStore
                }
            }

            // Insert-mode: the "+" is an absolute overlay anchored to the RIGHT of the gap (like dropZoneRegion,
            // out of flow) so it ADDS an insertion point without displacing, resizing, or covering the centred
            // channel pipe / its caption. Skipped over an expanded editor, where a "+" makes no sense.
            if (state.creating && !expanded) {
                div {
                    css {
                        position = Position.absolute
                        top = 0.px
                        bottom = 0.px
                        right = 1.em
                        display = Display.flex
                        alignItems = AlignItems.center
                    }
                    insertionButton(gapIndex)
                }
            }
        }
    }


    // Clear a channel's customization: remove the whole `channels.<outputPort>` entry (collapsing the `channels`
    // map if it was the only port) so the channel reverts to the Job-wide default, and collapse the inline editor
    // back to the compact chevron (there's nothing left to edit). Compute the next expansion set OUTSIDE the
    // write-only setState lambda (wrap/React.kt caveat).
    private fun clearChannelConfig(workerLocation: ObjectLocation, outputPort: AttributeName) {
        val key = workerLocation.toReference().asString()
        val next = (state.expandedChannels ?: emptySet()) - key
        setState {
            expandedChannels = next
        }

        // A default channel carries no override — `channels` is undeclared in the Worker's meta, so there is no
        // container to remove and RemoveInAttributeCommand would throw server-side ("Structured container
        // expected: channels - null"). Deleting an already-default channel is a no-op; collapsing the editor
        // above is the whole action.
        val configPath = JobConventions.workerOutputConfigPath(outputPort)
        val graphNotation = props.clientStateGlobal.current()
            ?.graphStructure()?.graphNotation
            ?: return
        val hasOverride = graphNotation.firstAttribute(workerLocation, configPath) != null
        if (!hasOverride) {
            return
        }

        async {
            props.mirroredGraphStore.apply(RemoveInAttributeCommand(
                workerLocation, configPath, true))
        }
    }


    // The gap at this index is the active drop target. Source no-op suppression: the dragged card's own two
    // edges (source / source+1) are no-ops, so don't highlight them (mirrors performShift's guard).
    private fun isActiveDropGap(gapIndex: Int): Boolean {
        val insertionIndex = state.dropInsertionIndex
            ?: return false
        if (insertionIndex != gapIndex) {
            return false
        }
        val source = state.dragSourceIndex
            ?: return true
        return !(gapIndex == source || gapIndex == source + 1)
    }


    private fun ChildrenBuilder.insertionButton(gapIndex: Int) {
        IconButton {
            title = "Insert here"

            sx {
                width = 32.px
                height = 32.px
                padding = 0.px
                backgroundColor = NamedColor.white

                hover {
                    backgroundColor = NamedColor.white
                }
            }

            onClick = {
                onCreate(gapIndex)
            }

            icon("material-symbols:add-circle-outline") {
                style = unsafeJso {
                    fontSize = 1.5.em
                }
            }
        }
    }
}
