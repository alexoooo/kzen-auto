package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.window
import mui.material.IconButton
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.bridge.InsertionKey
import tech.kzen.auto.client.objects.document.common.dragdrop.dropZoneRegion
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepSlot
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.display.image.StepImageThumbnail
import tech.kzen.auto.client.objects.document.script.model.ScriptDragStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.RelocateObjectTreeRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.animations.requestAnimationFrame
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepListDisplayProps: Props {
    var attributeLocation: AttributeLocation
    var nested: Boolean

    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
    var objectStableMapper: ObjectStableMapper
}


external interface StepListDisplayState: State {
    var stepLocations: List<ObjectLocation>?
    var dependencyEdges: StepDependencyEdges?

    var creating: Boolean

    // Shared across branches (set from ScriptStepDragStore); null when no drag is in progress.
    var dragSource: ScriptStepDragStore.DragSource?
    // The canonical insertion index (0..stepCount) for a drop in THIS branch, or null when the cursor is over
    // some other branch. The branch is one drop zone; the index is computed from cursor Y vs step midpoints,
    // so the whole vertical extent (cards, gaps, insertion strips, empty space) maps to a single line.
    var dropInsertionIndex: Int?
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptBranchDisplay(
    props: StepListDisplayProps
):
    RPureComponent<StepListDisplayProps, StepListDisplayState>(props),
    ClientStateGlobal.Observer,
    InsertionGlobal.Subscriber,
    ScriptStepDragStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val dragHandleColor = Color("rgba(0, 0, 0, 0.45)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Constructed once per instance, so these are ===-stable across renders and let each ScriptStepSlot
    // (RPureComponent) bail when only a sibling changed. The slot threads its own indexInParent back in,
    // so a single shared reference serves every slot rather than a fresh closure per slot per render.
    // Drag-over / drop are handled at the BRANCH level (one drop zone), not per slot — see onBranchDragOver.
    private val onSlotDragStart: (Int) -> Unit = { index -> onDragStart(index) }
    private val onSlotDragEnd: () -> Unit = { onDragEnd() }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepListDisplayState.init(props: StepListDisplayProps) {
        creating = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        insertion()?.subscribe(this)
        dragStore()?.observe(this)
    }


    override fun componentWillUnmount() {
        dragStore()?.unobserve(this)
        insertion()?.unsubscribe(this)
        props.clientStateGlobal.unobserve(this)
    }


    private fun dragStore(): ScriptStepDragStore? =
        contextValue<DocumentBridge?>()?.lookup(ScriptDragStoreKey)


    private fun insertion(): InsertionGlobal? =
        contextValue<DocumentBridge?>()?.channel(InsertionKey)


    // Derive only this branch's slice from the shared store and skip setState when unchanged, so a hover move
    // re-renders at most the branch losing the marker and the branch gaining it (see ScriptStepDragStore).
    override fun onDragStateChanged() {
        val store = dragStore()
            ?: return

        val dragSource = store.dragSource
        val dropInsertionIndex = store.dropHover
            ?.takeIf { it.branchLocation == props.attributeLocation }
            ?.insertionIndex

        if (state.dragSource == dragSource &&
            state.dropInsertionIndex == dropInsertionIndex
        ) {
            return
        }

        setState {
            this.dragSource = dragSource
            this.dropInsertionIndex = dropInsertionIndex
        }
    }


    // Capture window scroll just before React commits this branch's DOM mutations. The stage is
    // window-scrolled (no overflow container — see ProjectController), so scrollY is the position
    // to preserve.
    override fun getSnapshotBeforeUpdate(
        prevProps: StepListDisplayProps,
        prevState: StepListDisplayState
    ): Any {
        return window.scrollY
    }


    // The browser does not reliably hold window scroll when step rows are added/removed (scroll
    // anchoring fails on the structural change, jumping the viewport to the top); restore the
    // captured position so it stays put. Gated on a row-count change so in-place updates (drag
    // hover, dependency edges, attribute edits — which never moved scroll) are untouched.
    override fun componentDidUpdate(
        prevProps: StepListDisplayProps,
        prevState: StepListDisplayState,
        snapshot: Any
    ) {
        if (prevState.stepLocations?.size == state.stepLocations?.size) {
            return
        }

        val targetScrollY = snapshot as Double

        // Restore synchronously, then again on the next animation frame. ScriptDependencyOverlay
        // (a sibling of the top-level branch) reacts to the row-ref churn by scheduling a remeasure
        // in requestAnimationFrame; that remeasure calls getBoundingClientRect, forcing a layout in
        // which the browser re-applies its failed anchoring and re-jumps the window — clobbering the
        // synchronous restore. Our rAF is scheduled from here (commit layout phase, after the
        // overlay's was scheduled during the mutation phase), so it runs last and has the final say
        // before paint. Nested branches have no such sibling overlay, so the sync restore already
        // sufficed there; the rAF restore is a harmless no-op for them.
        window.scrollTo(window.scrollX, targetScrollY)
        requestAnimationFrame { window.scrollTo(window.scrollX, targetScrollY) }
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure

        if (props.attributeLocation.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val stepLocations = ScriptController.stepLocations(
            graphStructure, props.attributeLocation)

        val dependencyEdges = stepLocations?.let { steps ->
            val documentPath = props.attributeLocation.objectLocation.documentPath
            val documentNotation = graphStructure.graphNotation.documents[documentPath]
            if (documentNotation == null || !ScriptConventions.isScript(documentNotation)) {
                StepDependencyEdges.EMPTY
            }
            else {
                val analysis = ScriptDependencyAnalysis.analyze(clientState.graphDefinitionAttempt, documentPath)
                StepDependencyEdges.compute(steps, analysis)
            }
        }

        setState {
            this.stepLocations = stepLocations
            this.dependencyEdges = dependencyEdges
        }
    }


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


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(index: Int) {
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return

        val archetypeObjectLocation = insertion()
            ?.getAndClearSelection()
            ?: return

        val commands = props.scriptCommander.createCommands(
            props.attributeLocation,
            index,
            archetypeObjectLocation,
            graphStructure
        )

        async {
            for (command in commands) {
                props.mirroredGraphStore.apply(command)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onDragStart(sourceIndex: Int) {
        val stepLocations = state.stepLocations
            ?: return
        val draggedLocation = stepLocations.getOrNull(sourceIndex)
            ?: return

        // Publishing through the shared store notifies every branch (incl. this one, via onDragStateChanged).
        dragStore()?.begin(ScriptStepDragStore.DragSource(
            draggedLocation, props.attributeLocation, sourceIndex))
    }


    // The whole branch is one drop zone: claim a single canonical insertion index from the cursor's Y
    // against the step-row midpoints (cards, gaps, insertion strips, and empty space all map to one region).
    // Bound to BOTH dragenter and dragover: dragover is continuous but Chrome can be slow to re-fire it on
    // the inner branch right after an enclosing region handled it, whereas dragenter fires once, guaranteed,
    // on the boundary crossing — so the sibling branch claims the hover even on the "hover the If body first"
    // path. (dragleave is deliberately not used — it's flaky; hand-off happens by the next branch re-claiming
    // and by the window dragend in ScriptStepDragStore.)
    private fun onBranchDragOver(event: DragEvent<HTMLDivElement>) {
        claimDropHover(event)
    }


    private fun onBranchDragEnter(event: DragEvent<HTMLDivElement>) {
        claimDropHover(event)
    }


    private fun claimDropHover(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()

        // Read the drag source from the STORE (set synchronously in begin()), not React state — state
        // arrives asynchronously via the observer → setState path, so a branch could otherwise receive an
        // event before its own dragSource state has committed and bail without claiming the hover (the
        // cross-branch "via the header first" failure). onBranchDrop reads the store for the same reason.
        val store = dragStore()
            ?: return
        val dragSource = store.dragSource
            ?: return
        if (isInsideDraggedSubtree(dragSource)) {
            // This branch lives inside the dragged subtree — a drop here would be a cycle, so don't accept it.
            return
        }

        // Claim the event so it doesn't bubble to an enclosing branch (a nested branch is DOM-nested inside
        // its container step's row in the parent branch). Without this the parent branch's handler fires next
        // and overwrites the hover back to itself — so a step could never be dropped INTO a nested branch.
        event.stopPropagation()

        val stepLocations = state.stepLocations
            ?: return
        val insertionIndex = computeInsertionFromCursor(event.clientY, stepLocations)

        // Push to the shared hover so other branches drop their stale region; our own slice flows back via
        // onDragStateChanged.
        store.hover(ScriptStepDragStore.DropHover(props.attributeLocation, insertionIndex))
    }


    private fun onDragEnd() {
        dragStore()?.clear()
    }


    private fun onBranchDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()
        // Innermost branch under the cursor handles the drop; don't let it bubble to the enclosing branch.
        event.stopPropagation()

        // Read straight from the store (not React state) so the drop doesn't depend on the last dragover's
        // setState having committed; the store is updated synchronously on every dragover.
        val store = dragStore()
        val dragSource = store?.dragSource
        val hover = store?.dropHover
        store?.clear()

        if (dragSource == null || hover == null) {
            return
        }
        // The drop landed on this branch's element, so the live hover should be ours; bail if not (defensive).
        if (hover.branchLocation != props.attributeLocation) {
            return
        }
        if (isInsideDraggedSubtree(dragSource)) {
            return
        }

        val insertionIndex = hover.insertionIndex

        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return
        val documentPath = props.attributeLocation.objectLocation.documentPath
        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return

        val draggedLocation = dragSource.objectLocation
        val draggedRoot = draggedLocation.objectPath

        // Document order with the dragged subtree removed — the frame the relocate index resolves against.
        val remainingPaths = documentNotation.objects.notations.map.keys.filter {
            it != draggedRoot && !it.startsWith(draggedRoot)
        }

        val sameBranch = dragSource.branchLocation == props.attributeLocation

        if (sameBranch) {
            // Reorder within this branch. insertionIndex is in the pre-removal list, so dropping at the
            // dragged step's own two edges (source / source+1) is a no-op; otherwise account for the step
            // leaving its slot when it sits above the target.
            val source = dragSource.indexInBranch
            if (insertionIndex == source || insertionIndex == source + 1) {
                return
            }
            val newIndex = if (insertionIndex > source) insertionIndex - 1 else insertionIndex
            val siblings = (state.stepLocations ?: return).filterIndexed { i, _ -> i != source }
            val targetDocumentIndex = resolveTargetDocumentIndex(remainingPaths, siblings, newIndex)

            async {
                props.mirroredGraphStore.apply(ShiftObjectTreeCommand(
                    draggedLocation,
                    PositionRelation.at(targetDocumentIndex)))
            }
        }
        else {
            // Re-parent into this branch: the dragged step isn't in this list, so insertionIndex is direct.
            val siblings = state.stepLocations ?: listOf()
            val targetDocumentIndex = resolveTargetDocumentIndex(remainingPaths, siblings, insertionIndex)

            val newNesting = newBranchNesting(draggedLocation.objectPath.name)

            async {
                props.mirroredGraphStore.apply(RelocateObjectTreeRefactorCommand(
                    draggedLocation,
                    newNesting,
                    PositionRelation.at(targetDocumentIndex)))
            }
        }
    }


    // Insertion index (0..size) = the number of step rows whose vertical midpoint is above the cursor; rows
    // come from StepRowRefRegistry, in document order. An unregistered row is skipped (shouldn't happen for a
    // visible row); an empty branch yields 0.
    private fun computeInsertionFromCursor(
        clientY: Double,
        stepLocations: List<ObjectLocation>
    ): Int {
        var index = 0
        for (stepLocation in stepLocations) {
            val element = StepRowRefRegistry.get(stepLocation)
                ?: continue
            val rect = element.getBoundingClientRect()
            if (clientY < rect.top + rect.height / 2) {
                break
            }
            index++
        }
        return index
    }


    // True when this branch's containing object is the dragged subtree itself or one of its descendants —
    // dropping here would nest a container inside its own subtree (a cycle), which we reject.
    private fun isInsideDraggedSubtree(dragSource: ScriptStepDragStore.DragSource): Boolean {
        val container = props.attributeLocation.objectLocation
        if (container.documentPath != dragSource.objectLocation.documentPath) {
            return false
        }
        val draggedRoot = dragSource.objectLocation.objectPath
        return container.objectPath == draggedRoot || container.objectPath.startsWith(draggedRoot)
    }


    // The dragged root's nesting once re-parented under this branch's attribute.
    private fun newBranchNesting(draggedName: ObjectName): ObjectNesting {
        return props.attributeLocation.objectLocation.objectPath
            .nest(props.attributeLocation.attributePath, draggedName)
            .nesting
    }


    // The document insertion index (resolved against the doc with the dragged subtree removed) for placing the
    // subtree at newIndex among siblings. anchor = the sibling it should precede; null = goes after the last
    // sibling's subtree, or — for an empty branch — after this branch's containing object's whole subtree so it
    // serializes inside the branch region.
    private fun resolveTargetDocumentIndex(
        remainingPaths: List<ObjectPath>,
        siblings: List<ObjectLocation>,
        newIndex: Int
    ): Int {
        val anchor = siblings.getOrNull(newIndex)?.objectPath
        if (anchor != null) {
            return remainingPaths.indexOf(anchor)
        }
        if (siblings.isNotEmpty()) {
            val lastSibling = siblings.last().objectPath
            return remainingPaths.indexOfLast { it == lastSibling || it.startsWith(lastSibling) } + 1
        }
        val containerPath = props.attributeLocation.objectLocation.objectPath
        return remainingPaths.indexOfLast { it == containerPath || it.startsWith(containerPath) } + 1
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val stepLocations = state.stepLocations
            ?: return

        div {
            // NB: data-step-branch marks this branch's gap/padding as a "yield zone" so an enclosing slot's drag
            //     handle stays hidden when the cursor sits here (see ScriptStepSlot's :has() rule). Pure attribute
            //     + CSS — no ref/registry, so mouse movement over the branch triggers no React re-render.
            asDynamic()["data-step-branch"] = ""

            // The whole branch is the drop zone (one region, computed from cursor Y in claimDropHover). A
            // nested branch's own zone is inside this one; it stopPropagation()s so the innermost branch wins.
            onDragEnter = { event -> onBranchDragEnter(event) }
            onDragOver = { event -> onBranchDragOver(event) }
            onDrop = { event -> onBranchDrop(event) }

            if (stepLocations.isEmpty()) {
                div {
                    // position:relative anchors the drop region shown when dragging into this empty branch.
                    css {
                        position = Position.relative
                        paddingTop = 2.em
                    }

                    // The empty region ITSELF is the drop zone: fill it rather than draw a line at the top.
                    // (An empty branch is never the drag source, so no no-op suppression is needed here, and
                    // the nested firstOrLastInsertionPoint suppresses its own band so they don't double up.)
                    if (state.dropInsertionIndex == 0) {
                        dropZoneRegion()
                    }

                    div {
                        css {
                            fontSize = 1.5.em
                        }

                        if (props.nested) {
                            +"Add steps from the toolbar (above)"
                        }
                        else {
                            +"Empty script, please add steps from the toolbar (above)"
                        }
                    }

                    firstOrLastInsertionPoint(0, StepDependencyEdges.EMPTY, showDropZone = false)
                }
            }
            else {
                nonEmptySteps(stepLocations)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.nonEmptySteps(
        stepLocations: List<ObjectLocation>
    ) {
        val edges = state.dependencyEdges
            ?: StepDependencyEdges.EMPTY

        firstOrLastInsertionPoint(0, edges)

        div {
            for ((index, stepLocation) in stepLocations.withIndex()) {
                renderRowWithGutter(
                    stepLocation = stepLocation,
                    gutter = { stepDependencyGutterCellForStep(index, edges) },
                    body = { renderStep(index, stepLocation, stepLocations.size) })

                if (index < stepLocations.size - 1) {
                    renderRowWithGutter(
                        stepLocation = null,
                        gutter = { stepDependencyGutterCellForBetween(index, edges) },
                        body = { betweenStepsInsertionPoint(index + 1) })
                }
            }
        }

        firstOrLastInsertionPoint(stepLocations.size, edges)
    }


    private fun ChildrenBuilder.renderRowWithGutter(
        stepLocation: ObjectLocation?,
        gutter: ChildrenBuilder.() -> Unit,
        body: ChildrenBuilder.() -> Unit
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.stretch
            }
            if (stepLocation != null) {
                // NB: ref attaches to the OUTER row (gutter + body) so the overlay can compute the
                //     polyline endpoint at row.left + laneWidth/2 — the phantom column's x.
                //     React 19 invokes the returned Cleanup on unmount/ref-detach.
                ref = refCallback { element ->
                    StepRowRefRegistry.register(stepLocation, element)
                    val cleanup: () -> Unit = { StepRowRefRegistry.unregister(stepLocation, element) }
                    cleanup
                }
            }
            gutter()
            div {
                css {
                    width = ScriptController.stepWidth
                    flexShrink = number(0.0)
                    // NB: dedicated strip for the absolute-positioned drag handle (left: -1.25em
                    // off body's left edge). Without this margin, the handle overlaps the
                    // rightmost dependency-gutter lane.
                    marginLeft = 1.25.em
                }
                body()
            }
            if (stepLocation != null) {
                StepImageThumbnail::class.react {
                    objectLocation = stepLocation
                    objectStableMapper = props.objectStableMapper
                    clientStateGlobal = props.clientStateGlobal
                }
            }
        }
    }


    private fun ChildrenBuilder.betweenStepsInsertionPoint(index: Int) {
        // NB: flex with a single child left-aligns by default; alignItems=center vertically centers
        //     the +button in the 1.5em gap. left edge here is the step card's left edge (this body
        //     cell is offset marginLeft=1.25em past the dependency gutter in renderRowWithGutter),
        //     so the button stays clear of trunk lines drawn in the gutter to its left.
        // position:relative anchors the drop region (which fills this gap strip — the space between
        // the two cards — when this is the active insertion point).
        div {
            css {
                position = Position.relative
                display = Display.flex
                alignItems = AlignItems.center
                height = 1.5.em
                width = 100.pct
            }

            if (isActiveDropGap(index)) {
                dropZoneRegion()
            }

            insertionButton(index)
        }
    }


    private fun ChildrenBuilder.firstOrLastInsertionPoint(
        index: Int,
        edges: StepDependencyEdges,
        showDropZone: Boolean = true
    ) {
        // NB: routed through renderRowWithGutter (like betweenStepsInsertionPoint) so the +button
        //     lands in the same card-left column as the between-steps +buttons even when a
        //     dependency gutter widens the rows — the gutter's empty lane/phantom boxes reserve the
        //     identical left offset. `index - 1` is the step above this insertion point (mirrors
        //     the between-steps `stepDependencyGutterCellForBetween(index, ...)` for insertion point
        //     `index + 1`); at the first/last boundary no lane spans the gap, so it only reserves
        //     width and draws no trunk line.
        renderRowWithGutter(
            stepLocation = null,
            gutter = { stepDependencyGutterCellForBetween(index - 1, edges) },
            body = {
                // NB: render the placeholder unconditionally so toggling insertion mode never
                //     shifts layout. The 32px reservation also doubles as breathing room above/
                //     below the step list. `insertionButton` itself is gated by `state.creating`,
                //     so the visible "+" only appears when an archetype is selected. The branch-
                //     indent strip in `scriptBranchContainer` uses `background-clip: content-box`
                //     with matching 32px vertical padding so its white bg does NOT extend over
                //     these placeholder regions.
                // position:relative anchors the drop region for the top/bottom insertion points.
                // showDropZone=false in the empty-branch case, where the whole region is filled instead.
                div {
                    css {
                        position = Position.relative
                        height = 30.px
                        marginTop = 2.px
                    }

                    if (showDropZone && isActiveDropGap(index)) {
                        dropZoneRegion()
                    }

                    insertionButton(index)
                }
            })
    }


    // The gap at this insertion index is the active drop target. Source-branch no-op suppression: when
    // this branch is the drag source, the dragged step's own two edges (source / source+1) are no-ops
    // (mirrors onBranchDrop's same-branch guard), so don't highlight them.
    private fun isActiveDropGap(gapIndex: Int): Boolean {
        val insertionIndex = state.dropInsertionIndex
            ?: return false
        if (insertionIndex != gapIndex) {
            return false
        }
        val source = state.dragSource
            ?.takeIf { it.branchLocation == props.attributeLocation }
            ?.indexInBranch
        return !(source != null && (insertionIndex == source || insertionIndex == source + 1))
    }


    private fun ChildrenBuilder.insertionButton(index: Int) {
        if (!state.creating) {
            return
        }

        IconButton {
            title = "Insert step here"

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
                onCreate(index)
            }

            icon("material-symbols:add-circle-outline") {
                style = unsafeJso {
                    fontSize = 1.5.em
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderStep(
        index: Int,
        objectLocation: ObjectLocation,
        stepCount: Int
    ) {
        val dragSource = state.dragSource

        // This branch is the source iff the active drag began here; its index is the slot being dragged.
        val sourceIndexInThisBranch = dragSource
            ?.takeIf { it.branchLocation == props.attributeLocation }
            ?.indexInBranch

        ScriptStepSlot::class.react {
            key = Key(objectLocation.toReference().asString())

            this.objectLocation = objectLocation
            this.indexInParent = index
            this.first = index == 0
            this.last = index == stepCount - 1

            this.isDragSource = sourceIndexInThisBranch == index

            this.stepDisplayManager = props.stepDisplayManager
            this.handleColor = dragHandleColor

            this.onDragStart = onSlotDragStart
            this.onDragEnd = onSlotDragEnd
        }
    }
}
