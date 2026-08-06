package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import mui.material.Button
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.dragdrop.computeDropIndex
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.objects.document.common.dragdrop.dropMarkerFor
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.*
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptBranchDisplay
import tech.kzen.auto.client.objects.document.script.display.dependency.StepDependencyEdges
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRowBodyInset
import tech.kzen.auto.client.objects.document.script.display.dependency.stepDependencyAnchorLane
import tech.kzen.auto.client.objects.document.script.model.scriptDependencyAnalysis
import tech.kzen.auto.client.objects.document.script.model.stepRowRefRegistry
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*
import web.data.DropEffect
import web.data.move
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface IfStepDisplayProps: BranchStepDisplayProps {
    // The archetype "+ Else if" instantiates (IfBranch), resolved from notation — see script-js.yaml.
    var branchArchetype: ObjectLocation
}


external interface IfStepDisplayState: ScriptStepDisplayBaseState {
    // The chain's condition branches in order (document order of the IfBranch objects). Null until the first
    // client-state publish.
    var branchLocations: List<ObjectLocation>?

    // Whether the trailing Else section has any steps — it renders as a ghost when it does not.
    var elseEmpty: Boolean?

    // Per-branch gutter lanes for the condition sub-header rows. A condition's consumers are outside the
    // branch, so its edges are always cross-branch: this carries the phantom marker ScriptDependencyOverlay's
    // polyline terminates in.
    var branchEdges: Map<ObjectLocation, StepDependencyEdges>?

    // Branch reordering, deliberately LOCAL rather than in ScriptStepDragStore: a branch drag is scoped to one
    // If, and every other If (and every step branch) ignores it because their own drag index is null.
    var branchDragIndex: Int?
    var branchDragOverIndex: Int?
    var branchDropAfter: Boolean?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A segmented if / else-if / ... / else chain: the title slab, then per condition branch a white slab carrying
 * that branch's condition over a recessed stage carrying its steps, then the Else zone, always last. Else has
 * no condition, so its slab is a contentless ledge and its label floats over the top of its own stage.
 *
 * The chain carries no positional labels: precedence is position, first true wins, and the layout is what says
 * so. A branch is a nested IfBranch object, so the chain's order is those objects' document order — which is
 * why reordering is one [ShiftObjectTreeCommand] that renames nothing, and removing one is the same
 * deepest-first subtree walk a step removal uses. The branch objects carry stable identity names ("Branch",
 * "Branch 2", ...) that are never renumbered and DELIBERATELY never shown.
 */
@Suppress("unused")
class IfStepDisplay(
    props: IfStepDisplayProps
):
    ScriptStepDisplayBase<IfStepDisplayProps, IfStepDisplayState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // IfStep's own branch names, deliberately NOT in ScriptConventions: shared code discovers branches from
        // attribute metadata (`group: true` / `is: List, of: ScriptStep`), so nothing outside this display
        // needs to know them.
        private val branchesAttributeName = AttributeName("branches")
        private val branchesAttributePath = AttributePath.ofName(branchesAttributeName)

        private val elseAttributeName = AttributeName("else")
        private val elseAttributePath = AttributePath.ofName(elseAttributeName)

        // Else label: heading weight over the steps it groups, in the same subdued ink as DoWhile's "While".
        private val branchLabelColor = Color("rgba(0, 0, 0, 0.7)")

        private val branchDragHandleColor = Color("rgba(0, 0, 0, 0.45)")

        // Every section is one keyed wrapper, so adding, removing or reordering a branch moves whole nodes
        // instead of index-shifting the sections after it — which would reconcile a branch's step subtree, and
        // its async-hydrating condition editor, against a different branch's.
        private val elseSectionKey = Key("else")

        // The band the floated Else caption centres itself in: the strip ScriptBranchDisplay reserves above a
        // branch's first step for its leading insertion point, which is empty whenever no insertion is armed.
        private val elseCaptionHeight = 32.px

        // How far the section under the cursor's grip recedes while it is being dragged.
        private val draggedSectionOpacity = number(0.5)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val branchArchetype: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val scriptCommander: ScriptCommander,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            IfStepDisplay::class.react {
                branchArchetype = this@Wrapper.branchArchetype
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                scriptCommander = this@Wrapper.scriptCommander
                clientStateGlobal = this@Wrapper.clientStateGlobal
                objectStableMapper = this@Wrapper.objectStableMapper
                mirroredGraphStore = this@Wrapper.mirroredGraphStore

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The chain's shape and its condition rows' gutter lanes. Derived here rather than in onScriptStateExtra
    // because the memoized dependency analysis is keyed on the client state.
    override fun onClientStateExtra(clientState: ClientState) {
        val graphNotation = clientState.graphDefinitionAttempt.graphStructure.graphNotation
        val self = props.common.objectLocation
        if (self !in graphNotation.coalesce) {
            // NB: stale location (this If was deleted or renamed) — nothing to derive.
            return
        }

        val branchLocations = ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(self, branchesAttributePath))

        val elseStepLocations = ScriptConventions
            .orderedDirectChildLocations(graphNotation, AttributeLocation(self, elseAttributePath))
        val elseEmpty = elseStepLocations.isEmpty()

        val analysis = scriptDependencyAnalysis(clientState, self.documentPath)
        val branchEdges = branchLocations.associateWith {
            StepDependencyEdges.compute(listOf(it), analysis)
        }

        // NB: value compare (==) — every derived value is freshly allocated each fire, so a reference guard
        //     would never bail and each progress tick would re-render the whole construct.
        if (state.branchLocations == branchLocations &&
            state.elseEmpty == elseEmpty &&
            state.branchEdges == branchEdges
        ) {
            return
        }

        setState {
            this.branchLocations = branchLocations
            this.elseEmpty = elseEmpty
            this.branchEdges = branchEdges
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddBranch() {
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return

        // Appended at the end of the chain. createStep is safe for a non-step archetype: the generated name
        // comes from IfBranch's own `title` ("Branch" -> "Branch 2" -> ...), and no commander is registered
        // against IfBranch, so no seeding cascade fires.
        val stepCreation = props.scriptCommander.createStep(
            AttributeLocation(props.common.objectLocation, branchesAttributePath),
            state.branchLocations?.size ?: 0,
            props.branchArchetype,
            graphStructure)

        async {
            for (command in stepCreation.commands) {
                props.mirroredGraphStore.apply(command)
            }
        }
    }


    // Removing a branch cascade-deletes its steps: the same deepest-first subtree walk StepHeader.onRemove
    // does for a step, so every object is a leaf when it is removed.
    private fun onRemoveBranch(branchLocation: ObjectLocation) {
        async {
            val documentNotation = props.mirroredGraphStore.graphNotation()
                .documents[branchLocation.documentPath]
                ?: return@async

            val subtreePaths = documentNotation.objects.notations.map.keys
                .filter { it == branchLocation.objectPath || it.startsWith(branchLocation.objectPath) }
                .sortedByDescending { it.nesting.segments.size }

            for (objectPath in subtreePaths) {
                props.mirroredGraphStore.apply(RemoveObjectCommand(
                    ObjectLocation(branchLocation.documentPath, objectPath)))
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onBranchDragStart(index: Int) {
        setState {
            branchDragIndex = index
            branchDragOverIndex = null
            branchDropAfter = false
        }
    }


    private fun onBranchDragEnd() {
        clearBranchDrag()
    }


    private fun clearBranchDrag() {
        setState {
            branchDragIndex = null
            branchDragOverIndex = null
            branchDropAfter = false
        }
    }


    // Guarded on our OWN drag being in progress, so a step drag passing over a section is left entirely to
    // ScriptBranchDisplay (whose handlers are symmetrically guarded on the shared ScriptStepDragStore).
    //
    // Bound in the CAPTURE phase (see branchSection): a section wraps that branch's own ScriptBranchDisplay,
    // whose drop handler stopPropagation()s unconditionally ahead of its guards — so a branch released over a
    // section's steps could never bubble back out to the section. Claiming in capture also keeps a branch drag
    // from reaching the nested branch at all, while a step drag (guard false, nothing claimed) passes straight
    // through to it.
    //
    // [alwaysAbove] for the Else, which is not a branch and so has no "after": dropping on it can only mean
    // last in the chain.
    private fun onBranchDragOver(
        index: Int,
        event: DragEvent<HTMLDivElement>,
        alwaysAbove: Boolean = false
    ) {
        if (state.branchDragIndex == null) {
            return
        }
        event.preventDefault()
        event.stopPropagation()

        // Move cursor rather than the default copy. effectAllowed is uninitialized (treated as "all"), so
        // setting this on the target alone carries.
        event.dataTransfer.dropEffect = DropEffect.move

        val dropAfter =
            if (alwaysAbove) {
                false
            }
            else {
                val rect = event.currentTarget.getBoundingClientRect()
                event.clientY > rect.top + rect.height / 2
            }

        if (state.branchDragOverIndex == index && state.branchDropAfter == dropAfter) {
            return
        }
        setState {
            branchDragOverIndex = index
            branchDropAfter = dropAfter
        }
    }


    // Reorder within this If: one ShiftObjectTreeCommand moving the branch's whole subtree (its condition
    // attribute lives on the branch object, its steps nest under it) contiguously to the target document
    // index. Nothing is renamed, so stable ids, breakpoints, React keys and expand state all survive; `else`
    // children interleaved in the document are unaffected, since order only means anything within an attribute.
    private fun onBranchDrop(index: Int, event: DragEvent<HTMLDivElement>) {
        // Guard BEFORE claiming the event: in capture this sees every drop inside the section, including a
        // step being dropped into this branch's own list — which has to reach ScriptBranchDisplay untouched.
        val source = state.branchDragIndex
            ?: return
        event.preventDefault()
        event.stopPropagation()

        val dropAfter = state.branchDropAfter ?: false
        val branchLocations = state.branchLocations
        clearBranchDrag()

        if (branchLocations == null) {
            return
        }

        val newIndex = computeDropIndex(source, index, dropAfter)
        if (newIndex == source) {
            return
        }

        val dragged = branchLocations[source]
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return
        val documentNotation = graphStructure.graphNotation.documents[dragged.documentPath]
            ?: return

        val draggedRoot = dragged.objectPath

        // Document order with the dragged subtree removed — the frame PositionRelation.at resolves against.
        val remainingPaths = documentNotation.objects.notations.map.keys.filter {
            it != draggedRoot && !it.startsWith(draggedRoot)
        }

        val siblings = branchLocations.filterIndexed { i, _ -> i != source }
        val anchor = siblings.getOrNull(newIndex)?.objectPath

        val targetDocumentIndex =
            if (anchor != null) {
                remainingPaths.indexOf(anchor)
            }
            else {
                val lastSibling = siblings.last().objectPath
                remainingPaths.indexOfLast { it == lastSibling || it.startsWith(lastSibling) } + 1
            }

        async {
            props.mirroredGraphStore.apply(ShiftObjectTreeCommand(
                dragged,
                PositionRelation.at(targetDocumentIndex)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The title slab derives this same bar from the trace it is passed; each stage's rail and each
        // condition slab's border take it from here, so every segment of the construct's left edge matches.
        val trace = state.stepTrace
        val accent = ScriptStepDisplayDefault.statusBorderColor(
            trace?.state ?: StepTrace.State.Idle,
            trace?.error,
            state.isNextToRun ?: false,
            state.stepValidation?.errorMessage,
            state.stepValidation?.warningMessage)

        // No body: the conditions belong to the branches, one slab each, not to the If itself.
        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = trace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage,
            validationWarning = state.stepValidation?.warningMessage,
            isResult = state.isResult ?: false)

        val branchLocations = state.branchLocations ?: listOf()
        for ((index, branchLocation) in branchLocations.withIndex()) {
            renderBranch(accent, index, branchLocation, branchLocations.size)
        }

        // Dropping a branch here means "last in the chain" — the Else is always the end, so it has no
        // "after" of its own to aim at.
        branchSection(elseSectionKey, branchLocations.size, alwaysAbove = true) {
            // Opens the Else exactly as a condition opens its branch, minus the condition.
            branchSectionLedge(accent)

            renderBranchStage(accent, fadeBottom = true) {
                renderElseZone()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * A section of the chain as one drag/drop unit. The whole section moves and the whole section is a target,
     * so "drag it over the section you want to displace" works — the condition row alone would be a ~60px
     * strip in a section several hundred px tall.
     *
     * [index] is the branch's index, or the branch count for the Else (see [alwaysAbove]) — which is exactly
     * what [computeDropIndex] and [dropMarkerFor] want for "past the last branch", including their own no-op
     * suppression when the last branch is dragged onto it.
     */
    private fun ChildrenBuilder.branchSection(
        sectionKey: Key,
        index: Int,
        alwaysAbove: Boolean = false,
        content: ChildrenBuilder.() -> Unit
    ) {
        div {
            key = sectionKey

            css {
                // Anchors this section's drop line. No z-index at rest, so no stacking context — the slab's
                // own absolute children (divider, outset marker) keep the slab as their containing block.
                position = Position.relative

                if (state.branchDragIndex == index) {
                    // What says which section is travelling: the native drag image is the grip icon alone,
                    // which shows nothing of the steps coming with it.
                    opacity = draggedSectionOpacity
                }

                if (state.branchDragOverIndex == index) {
                    // Above the section BELOW this one, whose slab is position:relative and later in the DOM
                    // — without this it paints over a Below line sitting on the seam the two share.
                    zIndex = integer(1)
                }
            }

            onDragOverCapture = { event -> onBranchDragOver(index, event, alwaysAbove) }
            onDropCapture = { event -> onBranchDrop(index, event) }

            content()

            // Last, so it paints over this section's own surfaces. Offset zero because sections butt against
            // each other: the line lands ON the seam it will become, at the card's full width.
            dropIndicator(
                dropMarkerFor(
                    state.branchDragIndex,
                    state.branchDragOverIndex,
                    state.branchDropAfter ?: false,
                    index),
                offset = 0.px)
        }
    }


    // One section of the chain: the branch's condition on the construct's own white surface, over the recessed
    // stage holding that branch's steps.
    private fun ChildrenBuilder.renderBranch(
        accent: Color,
        index: Int,
        branchLocation: ObjectLocation,
        branchCount: Int
    ) {
        branchSection(Key(branchLocation.toReference().asString()), index) {
            branchSectionSlab(
                accent,
                outsetMarker = {
                    // The branch's own edges can only come from outside it, so this is the phantom target
                    // marker the overlay's polyline terminates in. Emitted whether or not there is one, so
                    // setting a condition on a fresh branch never shifts anything.
                    stepDependencyAnchorLane(
                        branchLocation,
                        stepRowRefRegistry(),
                        showTarget = state.branchEdges?.get(branchLocation)?.hasCrossBranch ?: false)
                }
            ) {
                renderConditionRow(index, branchLocation, branchCount)
            }

            renderBranchStage(accent, fadeBottom = false) {
                ScriptBranchDisplay::class.react {
                    attributeLocation = AttributeLocation(
                        branchLocation, ScriptConventions.stepsAttributePath)
                    nested = true
                    stepDisplayManager = props.stepDisplayManager
                    scriptCommander = props.scriptCommander
                    clientStateGlobal = props.clientStateGlobal
                    mirroredGraphStore = props.mirroredGraphStore
                    objectStableMapper = props.objectStableMapper
                }
            }
        }
    }


    // A section's recessed stage: the step list, under the seam and down-shadow cast by the white surface above
    // it, with the construct's status bar continuing down its left edge.
    //
    // [fadeBottom] only for the last stage in the chain (Else): every other one closes onto the next section's
    // white surface. No band overhang anywhere — the surface below opens with a divider that is meant to cross
    // the bar, so there is no border miter to cover (see branchStageAccentRail).
    private fun ChildrenBuilder.renderBranchStage(
        accent: Color,
        fadeBottom: Boolean,
        content: ChildrenBuilder.() -> Unit
    ) {
        div {
            css {
                // position:relative so the rail anchors to THIS stage, not the whole construct.
                position = Position.relative
            }

            branchStageAccentRail(accent, fadeBottom)

            branchStageSeam()
            branchStageTopShadow {
                div {
                    css {
                        // The rail is paint only (absolutely positioned over the stage's left edge), so this
                        // indent is the only thing holding the step rows off the scope line — and it is what
                        // puts them in the same dependency-gutter column as the condition rows above them.
                        marginLeft = branchRailWidth
                        minHeight = 4.em
                    }

                    content()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The branch's own row: condition editor, remove control, and the grip that drags the whole section.
    //
    // Not a scriptGutterRow — the marker the overlay terminates in sits OUTSIDE the card (see the slab's
    // outsetMarker), so there is no gutter here to lay out. What remains of that row's geometry is this one
    // inset: the slab's branchSlabContentPadding puts the row on the stage's own indent, and the margin then
    // measures out the same drag-handle strip a step card gets — so a condition and the steps it guards start
    // in one column.
    //
    // The grip is only anchored here: the enclosing slab's hover reveals it (branchSectionSlab) and the
    // enclosing section receives the drop (branchSection) — you aim at a header, but you aim for a section.
    //
    // Deliberately NOT marked data-step-header: ScriptExecutionMargin anchors on the If's OWN header row, and
    // its querySelector is document-first.
    private fun ChildrenBuilder.renderConditionRow(
        index: Int,
        branchLocation: ObjectLocation,
        branchCount: Int
    ) {
        div {
            css {
                // Containing block for the grip, which hangs off the row's left edge.
                position = Position.relative
                marginLeft = scriptGutterRowBodyInset
            }

            // Only offered once there is something to reorder.
            if (branchCount > 1) {
                dragHandle(
                    isVisible = state.branchDragIndex == index,
                    handleColor = branchDragHandleColor,
                    onStart = { onBranchDragStart(index) },
                    onEnd = { onBranchDragEnd() })
            }

            div {
                css {
                    display = Display.flex

                    // Top-aligned, not centred: the condition is a MULTILINE expression field that grows
                    // downward, and KotlinExpressionEditor top-aligns its own insert-reference button the same
                    // way — so remove and insert stay on one line with the field's first row however tall it
                    // gets, instead of drifting to the middle of a long expression.
                    alignItems = AlignItems.flexStart
                }

                div {
                    css {
                        flexGrow = number(1.0)
                        minWidth = 0.px
                    }

                    props.attributeEditorManager.child(this) {
                        this.objectLocation = branchLocation
                        this.attributeName = ScriptConventions.conditionAttributeName
                    }
                }

                // Removing the LAST condition branch is disallowed: an If with no branch at all is a
                // hand-edit, not something the editor should produce.
                if (branchCount > 1) {
                    IconButton {
                        title = "Remove branch"
                        size = Size.small

                        onClick = {
                            it.stopPropagation()
                            onRemoveBranch(branchLocation)
                        }

                        icon("material-symbols:delete") {}
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Always last, and always mounted even when empty: ScriptBranchDisplay is what accepts a toolbar insertion
    // or a step dropped into Else, so a "render nothing when empty" Else could never be filled. It also carries
    // the add-condition affordance, which is what leaves a hand-edited zero-branch If (legal — it executes as
    // else-only) fixable from the editor.
    private fun ChildrenBuilder.renderElseZone() {
        val ghost = state.elseEmpty ?: false

        renderElseCaption(ghost)

        // Empty, the step list is dimmed to a ghost so the chain reads as ending at the last condition.
        div {
            css {
                if (ghost) {
                    opacity = number(0.55)
                }
            }

            ScriptBranchDisplay::class.react {
                attributeLocation = AttributeLocation(props.common.objectLocation, elseAttributePath)
                nested = true
                stepDisplayManager = props.stepDisplayManager
                scriptCommander = props.scriptCommander
                clientStateGlobal = props.clientStateGlobal
                mirroredGraphStore = props.mirroredGraphStore
                objectStableMapper = props.objectStableMapper
            }
        }
    }


    // Else has no condition to slab, so it names itself: a caption floated across the top of its own stage,
    // label at the leading edge and the add control at the trailing one.
    //
    // Out of flow deliberately. The label is chrome, not a row — costing the Else 32px of height above its
    // first step would make it read as a section with something in it. It lands in the strip every branch
    // already reserves for its leading insertion point (see ScriptBranchDisplay.firstOrLastInsertionPoint),
    // which is empty by construction, and pointer-events pass through it so the branch keeps the whole of its
    // own drop zone — only the button takes clicks back.
    //
    // Its containing block is branchStageTopShadow's wrapper, i.e. the stage: `left` measures from the
    // construct's left edge (hence the rail width, matching the indent the steps get) and `right` from its
    // card edge.
    private fun ChildrenBuilder.renderElseCaption(ghost: Boolean) {
        div {
            css {
                position = Position.absolute
                top = 0.px
                left = branchRailWidth
                right = 0.px
                height = elseCaptionHeight
                display = Display.flex
                alignItems = AlignItems.center
                pointerEvents = None.none

                // Above the insertion strip it floats over. That strip is `position: relative` and comes
                // LATER in the branch's DOM, so without this it paints over the caption and swallows every
                // click aimed at the add control — the label still shows through (the strip draws nothing),
                // which is what makes the miss look like a dead button rather than an occlusion.
                zIndex = integer(1)
            }

            div {
                css {
                    flexGrow = number(1.0)
                    fontWeight = FontWeight.bold
                    color = branchLabelColor
                    if (ghost) {
                        opacity = number(0.55)
                    }
                }
                +"Else"
            }

            renderAddBranch()
        }
    }


    // Trails the Else caption, where the condition it adds lands: last in the chain, immediately above Else.
    // Never dimmed with an empty Else — that is the case where it is needed most.
    private fun ChildrenBuilder.renderAddBranch() {
        Button {
            size = Size.small

            sx {
                // Back on, against the caption's pass-through.
                pointerEvents = Auto.auto
            }

            onClick = {
                it.stopPropagation()
                onAddBranch()
            }

            icon("material-symbols:add") {}

            div {
                css {
                    marginLeft = 0.25.em
                    textTransform = None.none
                    color = branchLabelColor
                }
                +"Else if"
            }
        }
    }
}
