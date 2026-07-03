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
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayManager
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayPropsCommon
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
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
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
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
}


//---------------------------------------------------------------------------------------------------------------------
// The Job editor: a single document-order stage of Worker cards (white node cards with attribute editors, live
// status / counts, and — for a Preview worker — a live sample table), with the Channels connecting them drawn
// as gold pipes in the gaps between cards (JobChannelPipe). Channels are auto-managed: the pipeline is derived
// from Worker order + typed ports (JobChannelDerivation) and synthesized at run time, so the saved notation
// keeps Worker ports blank and carries no Channel objects on the common path. Worker cards can be reordered by
// drag/drop and the ribbon insert drops at a chosen position; reordering re-forms the pipes. Run / Step / Pause
// come from the shared logic ribbon, exactly like Script / Flow. A graphical node-and-edge canvas is deferred
// (see kzen/plans/2026-06-23_job-paradigm.md, M4).
@Suppress("unused")
class JobController(
    props: JobControllerProps
):
    RPureComponent<JobControllerProps, JobControllerState>(props),
    ClientStateGlobal.Observer,
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


    //-----------------------------------------------------------------------------------------------------------------
    private val jobProgressStore by lazy {
        JobProgressStore(props.restClient, props.objectStableMapper)
    }

    // Refetch progress only when the document or the run status changes (mirrors FlowController); during a run
    // the status time advances on each logic-status poll, so this also drives the live progress refresh.
    private var lastFetchKey: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun JobControllerState.init(props: JobControllerProps) {
        clientState = null
        workerProgress = null
        workerLocations = null
        connectionsByUpstream = null
        creating = false
        dragSourceIndex = null
        dropInsertionIndex = null
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
    }


    // Recompute the Worker list + derived pipes and store them ONLY when changed, so the held List /
    // ObjectLocation / Connection instances stay ===-stable across drag-hover and progress-poll re-renders —
    // that stability is what lets each JobObjectSlot and JobChannelPipe bail.
    private fun updateStageModel(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val graphStructure = clientState.graphStructure()
        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return
        if (! JobConventions.isJob(documentNotation)) {
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

        if (! JobConventions.isJob(documentNotation)) {
            return
        }

        // Key on the run-status time so each logic-status poll during a run refreshes the live progress, and
        // on the document path so opening a Job loads its last run's final progress.
        val logicStatusTime = clientState.clientLogicState.logicStatus?.time
        val fetchKey = "${documentPath.asString()}|$logicStatusTime"
        if (fetchKey == lastFetchKey) {
            return
        }
        lastFetchKey = fetchKey

        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val workerLocations = workerPaths(documentNotation)
            .map { ObjectLocation(documentPath, it) }

        async {
            val progress = jobProgressStore.fetchWorkerProgress(mainLocation, workerLocations)
            // Value-equality gate (data class + map): skip setState when nothing changed so RPureComponent
            // bails out instead of re-rendering on every (identical) poll.
            if (progress != state.workerProgress) {
                setState {
                    workerProgress = progress
                }
            }
        }

        // The per-Worker preview-slice and summary serve-channel pulls that used to live here are now owned by
        // each Worker's own card (PreviewWorkerDisplay / SummaryWorkerDisplay), which observe the run status and
        // pull their own serve channel — so this controller no longer knows about any Worker type (see CC-17).
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
        if (! JobConventions.isJob(documentNotation)) {
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

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! JobConventions.isJob(documentNotation)) {
            return
        }

        val workers = state.workerLocations ?: listOf()
        val connections = state.connectionsByUpstream ?: mapOf()
        val active = clientState.clientLogicState.isActive()

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            // The whole stage is one drop zone; the drop index is computed from the cursor Y (onStageDragOver).
            onDragEnter = { event -> onStageDragOver(event) }
            onDragOver = { event -> onStageDragOver(event) }
            onDrop = { event -> onStageDrop(event) }

            if (workers.isEmpty()) {
                div {
                    css {
                        fontSize = 1.25.em
                        marginBottom = 1.em
                    }
                    +"Empty Job — add Workers from the ribbon above."
                }
                insertionGap(0, null)
                return@div
            }

            insertionGap(0, null)
            for ((index, workerLocation) in workers.withIndex()) {
                renderWorkerSlot(index, workerLocation, active)

                // The pipe (if any) for this Worker lives in the gap directly below it (upstream = this Worker).
                // The last Worker's gap is a plain trailing insert / drop gap.
                val connection =
                    if (index < workers.size - 1) connections[workerLocation]
                    else null
                insertionGap(index + 1, connection)
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
    private fun ChildrenBuilder.insertionGap(gapIndex: Int, connection: JobChannelDerivation.Connection?) {
        div {
            css {
                position = Position.relative
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                maxWidth = 40.em
                height = when {
                    state.creating -> 2.em
                    connection != null -> 1.5.em
                    else -> 0.75.em
                }
            }

            if (isActiveDropGap(gapIndex)) {
                dropZoneRegion()
            }

            if (state.creating) {
                insertionButton(gapIndex)
            }
            else if (connection != null) {
                JobChannelPipe::class.react {
                    key = Key("pipe:" + connection.upstreamWorker.toReference().asString())
                    upstreamName = connection.upstreamWorker.objectPath.name.value
                    downstreamName = connection.downstreamWorker.objectPath.name.value
                }
            }
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
