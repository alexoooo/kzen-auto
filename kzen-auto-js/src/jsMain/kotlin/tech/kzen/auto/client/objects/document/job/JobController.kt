package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
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
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.dragdrop.dropZoneRegion
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
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
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
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface JobControllerState: State {
    var clientState: ClientState?

    // Per-Worker live progress (status + counts + preview teaser), polled from the logic trace store after
    // each run-status change. Null until the first fetch.
    var workerProgress: Map<ObjectLocation, JobWorkerProgress>?

    // On-demand larger sample pulled from a PreviewWorker over its duplex `serve` channel (Worker location ->
    // queried slice). Distinct from the always-on pushed teaser in [workerProgress]. Null until first query.
    var previewDetail: Map<ObjectLocation, JobWorkerProgress>?

    // All Worker + Channel locations in document order — the single unified stage list. Value-gated in
    // onClientState so the instances stay ===-stable across drag/progress re-renders, letting each
    // JobObjectSlot bail out (see updateUnifiedLocations).
    var unifiedLocations: List<ObjectLocation>?

    // Ribbon insert-mode: while true, a "+" insertion point shows in every gap so the user picks where the
    // selected archetype lands (mirrors ScriptBranchDisplay's `creating`).
    var creating: Boolean

    // Active drag: the index in [unifiedLocations] being dragged, and the cursor's insertion index (0..size)
    // computed from card midpoints. Both null when no drag is in progress.
    var dragSourceIndex: Int?
    var dropInsertionIndex: Int?
}


//---------------------------------------------------------------------------------------------------------------------
// The Job editor: the Worker / Channel graph the user assembles from the ribbon palette (header), rendered as a
// single document-order stage of cards (body). Workers are white node cards (attribute editors, live status /
// counts, and — for a Preview worker — a live sample table); Channels are gold pipe-styled bars echoing the
// Flow Pipe so connectors read distinctly from nodes. Cards can be reordered by drag/drop and the ribbon insert
// drops at a chosen position. Run / Step / Pause come from the shared logic ribbon, exactly like Script / Flow.
// Reordering / interleaving is purely cosmetic — Workers run concurrently and wire to Channels by typed
// reference, not position (see JobExecution). A graphical node-and-edge canvas is deferred (see
// kzen/plans/2026-06-23_job-paradigm.md, M4).
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
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
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
                        this.attributeEditorManager = this@Wrapper.attributeEditorManager
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
    private val onSlotDelete: (ObjectLocation) -> Unit = { location -> onDelete(location) }
    private val onSlotQueryPreview: (ObjectLocation) -> Unit = { location -> queryPreview(location) }


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
        previewDetail = null
        unifiedLocations = null
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

        updateUnifiedLocations(clientState)
        refreshProgressIfNeeded(clientState)
    }


    // Recompute the unified document-order list and store it ONLY when it changed structurally, so the held
    // List / ObjectLocation instances stay ===-stable across drag-hover and progress-poll re-renders — that
    // stability is what lets each JobObjectSlot bail.
    private fun updateUnifiedLocations(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return
        if (! JobConventions.isJob(documentNotation)) {
            return
        }

        val unified = computeUnifiedLocations(documentPath, documentNotation)
        if (unified != state.unifiedLocations) {
            setState {
                unifiedLocations = unified
            }
        }
    }


    // All objects nested under main via either `workers` or `channels`, in document order (the ordered
    // objects-notations map already is document order; we filter it by membership in the two attribute lists).
    private fun computeUnifiedLocations(
        documentPath: DocumentPath,
        documentNotation: DocumentNotation
    ): List<ObjectLocation> {
        val workers = workerPaths(documentNotation).toHashSet()
        val channels = channelPaths(documentNotation).toHashSet()
        return documentNotation.objects.notations.map.keys
            .filter { it in workers || it in channels }
            .map { ObjectLocation(documentPath, it) }
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

        // Keep any opened larger preview slice live: while the run is active, re-pull each over its duplex
        // serve channel on every poll (the Worker's rolling window means each pull is a fresh sample). When
        // the run ends, drop the live slices so the persisted final teaser shows instead.
        val active = clientState.clientLogicState.logicStatus?.active != null
        if (active) {
            val openedPreviewLocations = (state.previewDetail ?: mapOf()).keys
                .filter { it.documentPath == documentPath }
            if (openedPreviewLocations.isNotEmpty()) {
                async {
                    for (workerLocation in openedPreviewLocations) {
                        refreshPreviewSlice(clientState, workerLocation)
                    }
                }
            }
        }
        else if (state.previewDetail?.isNotEmpty() == true) {
            setState {
                previewDetail = mapOf()
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


    // Insert the ribbon-selected archetype at the clicked gap: a Channel nests under `channels`, a Worker under
    // `workers` (membership by archetype type), at the chosen document position. getAndClearSelection also fires
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


    // The document index at which to insert so the new object lands at the requested unified gap: before the
    // card currently occupying that slot, after the last card, or right after main when the stage is empty.
    // Workers / Channels are flat (no nested subtree), so this is a plain index lookup.
    private fun insertionDocumentIndex(documentNotation: DocumentNotation, gapIndex: Int): Int {
        val unified = state.unifiedLocations ?: listOf()
        return when {
            unified.isEmpty() ->
                documentNotation.indexOf(NotationConventions.mainObjectPath).value + 1

            gapIndex < unified.size ->
                documentNotation.indexOf(unified[gapIndex].objectPath).value

            else ->
                documentNotation.indexOf(unified.last().objectPath).value + 1
        }
    }


    private fun onDelete(objectLocation: ObjectLocation) {
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(objectLocation))
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

        val unified = state.unifiedLocations
            ?: return
        val insertionIndex = computeInsertionFromCursor(event.clientY, unified)
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
    private fun computeInsertionFromCursor(clientY: Double, unified: List<ObjectLocation>): Int {
        var index = 0
        for (objectLocation in unified) {
            val element = JobCardRowRegistry.get(objectLocation)
                ?: continue
            val rect = element.getBoundingClientRect()
            if (clientY < rect.top + rect.height / 2) {
                break
            }
            index++
        }
        return index
    }


    // Move the dragged card to the chosen position. insertionIndex is in the pre-removal list, so dropping at
    // the dragged card's own two edges is a no-op; otherwise account for the card leaving its slot when it sits
    // above the target. The target document index is resolved against the document with the dragged object
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
        val unified = state.unifiedLocations
            ?: return
        val draggedLocation = unified.getOrNull(source)
            ?: return
        val draggedPath = draggedLocation.objectPath

        val newIndex = if (insertionIndex > source) insertionIndex - 1 else insertionIndex
        val remainingPaths = documentNotation.objects.notations.map.keys.filter { it != draggedPath }
        val remainingUnified = unified.filterIndexed { i, _ -> i != source }

        val anchor = remainingUnified.getOrNull(newIndex)?.objectPath
        val targetDocumentIndex =
            if (anchor != null) {
                remainingPaths.indexOf(anchor)
            }
            else {
                val last = remainingUnified.lastOrNull()?.objectPath
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


    private fun channelPaths(documentNotation: DocumentNotation): List<ObjectPath> {
        return documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.channelsAttributeName)
    }


    private fun isPreviewWorker(documentNotation: DocumentNotation, workerPath: ObjectPath): Boolean {
        val workerIs = documentNotation.objects.notations[workerPath]
            ?.get(NotationConventions.isAttributeName)
            ?.asString()
        return workerIs == "PreviewWorker"
    }


    private fun isRunWorker(documentNotation: DocumentNotation, workerPath: ObjectPath): Boolean {
        val workerIs = documentNotation.objects.notations[workerPath]
            ?.get(NotationConventions.isAttributeName)
            ?.asString()
        return workerIs == "RunWorker"
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Show / start the larger live preview slice for a Preview worker: pulls it once now; while the run stays
    // active, refreshProgressIfNeeded keeps re-pulling it each poll (and clears it once the run ends).
    private fun queryPreview(workerLocation: ObjectLocation) {
        val clientState = state.clientState
            ?: return
        async {
            refreshPreviewSlice(clientState, workerLocation)
        }
    }


    // Pull one Preview worker's current slice over its duplex `serve` channel and store it, value-equality
    // gated so an unchanged slice doesn't re-render.
    private suspend fun refreshPreviewSlice(clientState: ClientState, workerLocation: ObjectLocation) {
        val parsed = queryPreviewSlice(clientState, workerLocation)
            ?: return

        if (parsed == state.previewDetail?.get(workerLocation)) {
            return
        }

        val updated = (state.previewDetail ?: mapOf()).plus(workerLocation to parsed)
        setState {
            previewDetail = updated
        }
    }


    // Issue an `offset` / `limit` slice query to a Preview worker over its (external) duplex `serve` channel,
    // via the running logic's request subscriber — the browser -> Worker request/reply path. The reply has the
    // same shape as the pushed teaser.
    private suspend fun queryPreviewSlice(
        clientState: ClientState,
        workerLocation: ObjectLocation
    ): JobWorkerProgress? {
        val logicRunInfo = clientState.clientLogicState.logicStatus?.active
            ?: return null

        val serveReference = clientState.graphStructure().graphNotation
            .firstAttribute(workerLocation, AttributePath.ofName(AttributeName("serve")))
            ?.asString()
            ?: return null
        val channelName = serveReference.substringAfterLast("/")

        val result = props.restClient.logicRequest(
            logicRunInfo.id,
            logicRunInfo.frame.executionId,
            JobConventions.channelParameter to channelName,
            JobConventions.previewOffsetParameter to "0",
            JobConventions.previewLimitParameter to "200")

        return when (result) {
            is ExecutionSuccess ->
                JobWorkerProgress.ofProgressMap(null, result.value.get())

            is ExecutionFailure ->
                null
        }
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

        val unifiedLocations = state.unifiedLocations ?: listOf()
        val graphStructure = clientState.graphStructure()
        val active = clientState.clientLogicState.isActive()
        val channelPathSet = channelPaths(documentNotation).toHashSet()

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            // The whole stage is one drop zone; the drop index is computed from the cursor Y (claimDropHover).
            onDragEnter = { event -> onStageDragOver(event) }
            onDragOver = { event -> onStageDragOver(event) }
            onDrop = { event -> onStageDrop(event) }

            if (unifiedLocations.isEmpty()) {
                div {
                    css {
                        fontSize = 1.25.em
                        marginBottom = 1.em
                    }
                    +"Empty Job — add Workers and Channels from the ribbon above."
                }
                insertionGap(0)
                return@div
            }

            insertionGap(0)
            for ((index, objectLocation) in unifiedLocations.withIndex()) {
                renderSlot(index, objectLocation, documentNotation, graphStructure, active, channelPathSet)
                insertionGap(index + 1)
            }
        }
    }


    private fun ChildrenBuilder.renderSlot(
        index: Int,
        objectLocation: ObjectLocation,
        documentNotation: DocumentNotation,
        graphStructure: GraphStructure,
        active: Boolean,
        channelPathSet: Set<ObjectPath>
    ) {
        val isChannel = objectLocation.objectPath in channelPathSet

        JobObjectSlot::class.react {
            key = Key(objectLocation.toReference().asString())

            this.objectLocation = objectLocation
            this.indexInParent = index
            this.isChannel = isChannel
            this.external = isChannel && JobConventions.isExternalChannel(documentNotation, objectLocation.objectPath)
            this.isPreviewWorker = ! isChannel && isPreviewWorker(documentNotation, objectLocation.objectPath)
            this.isRunWorker = ! isChannel && isRunWorker(documentNotation, objectLocation.objectPath)

            this.progress = state.workerProgress?.get(objectLocation)
            this.previewDetail = state.previewDetail?.get(objectLocation)
            this.active = active

            this.graphStructure = graphStructure
            this.attributeEditorManager = props.attributeEditorManager

            this.isDragSource = state.dragSourceIndex == index
            this.handleColor = dragHandleColor

            this.onDragStart = onSlotDragStart
            this.onDragEnd = onSlotDragEnd
            this.onDelete = onSlotDelete
            this.onQueryPreview = onSlotQueryPreview
        }
    }


    // A gap between cards (index 0 above the first, index size after the last): shows the drop indicator when
    // it's the active drop target, and a "+" insert button while in ribbon insert-mode. Height is reserved in
    // both modes so toggling never shifts the card layout.
    private fun ChildrenBuilder.insertionGap(gapIndex: Int) {
        div {
            css {
                position = Position.relative
                display = Display.flex
                alignItems = AlignItems.center
                maxWidth = 40.em
                height = if (state.creating) 2.em else 0.75.em
            }

            if (isActiveDropGap(gapIndex)) {
                dropZoneRegion()
            }

            if (state.creating) {
                insertionButton(gapIndex)
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

            css {
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
