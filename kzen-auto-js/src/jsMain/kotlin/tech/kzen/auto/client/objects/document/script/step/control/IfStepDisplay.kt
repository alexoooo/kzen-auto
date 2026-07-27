package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import mui.material.Button
import mui.material.IconButton
import mui.material.Size
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
import tech.kzen.auto.client.objects.document.script.display.dependency.scriptGutterRow
import tech.kzen.auto.client.objects.document.script.display.dependency.stepDependencyGutterCellForStep
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
 * An if / else-if / ... / else chain: one stage section per condition branch (each with its own condition
 * editor and step list), a "+ Else if" affordance, then the Else section, always last.
 *
 * A branch is a nested IfBranch object, so the chain's order is those objects' document order — which is why
 * reordering is one [ShiftObjectTreeCommand] that renames nothing, and removing one is the same deepest-first
 * subtree walk a step removal uses. The branch objects carry stable identity names ("Branch", "Branch 2", ...)
 * that are never renumbered and DELIBERATELY never shown: the labels here are positional ("If" for the first,
 * "Else if" for the rest), so what the reader sees is the chain's semantics, not its bookkeeping.
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

        // Branch label: heading weight over the steps it groups, in the same subdued ink as DoWhile's "While".
        private val branchLabelColor = Color("rgba(0, 0, 0, 0.7)")

        private val branchDragHandleColor = Color("rgba(0, 0, 0, 0.45)")
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

        val elseEmpty = ScriptConventions
            .orderedDirectChildLocations(graphNotation, AttributeLocation(self, elseAttributePath))
            .isEmpty()

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


    // Guarded on our OWN drag being in progress, so a step drag passing over a branch row is left entirely to
    // ScriptBranchDisplay (whose handlers are symmetrically guarded on the shared ScriptStepDragStore).
    private fun onBranchDragOver(index: Int, event: DragEvent<HTMLDivElement>) {
        if (state.branchDragIndex == null) {
            return
        }
        event.preventDefault()
        event.stopPropagation()

        val rect = event.currentTarget.getBoundingClientRect()
        val dropAfter = event.clientY > rect.top + rect.height / 2

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
        event.preventDefault()
        event.stopPropagation()

        val source = state.branchDragIndex
        val dropAfter = state.branchDropAfter ?: false
        val branchLocations = state.branchLocations
        clearBranchDrag()

        if (source == null || branchLocations == null) {
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
            it != draggedRoot && ! it.startsWith(draggedRoot)
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
        // The header slab derives this same bar from the trace it is passed; the stage's rail takes it from
        // here, so the construct shows one left edge from the header down through every section.
        val trace = state.stepTrace
        val accent = ScriptStepDisplayDefault.statusBorderColor(
            trace?.state ?: StepTrace.State.Idle,
            trace?.error,
            state.isNextToRun ?: false,
            state.stepValidation?.errorMessage)

        // No body: the conditions live on the branches, one per section, not on the If itself.
        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = trace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage)

        // One recessed stage spanning every section: a single rail down its whole height, since the whole
        // chain is one construct's scope. Each section then gets its own seam plus the down-shadow cast onto
        // it from the white surface above — the header slab for the first, the preceding section's lip for
        // the rest.
        div {
            css {
                position = Position.relative
            }

            branchStageAccentRail(accent, fadeBottom = true)

            val branchLocations = state.branchLocations ?: listOf()
            for ((index, branchLocation) in branchLocations.withIndex()) {
                renderConditionSection(index, branchLocation, branchLocations.size)
            }

            // A hand-edited zero-branch If is legal (it executes as else-only), so the affordance cannot live
            // only inside the last branch's section.
            if (branchLocations.isEmpty()) {
                branchStageSeam()
                div {
                    css {
                        position = Position.relative
                    }
                    branchStageTopShadow {
                        div {
                            css {
                                marginLeft = branchRailWidth
                            }
                            renderAddBranch()
                        }
                    }
                    branchStageSectionLip()
                }
            }

            branchStageSeam()
            branchStageTopShadow {
                renderElse()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderConditionSection(
        index: Int,
        branchLocation: ObjectLocation,
        branchCount: Int
    ) {
        branchStageSeam()

        div {
            key = Key(branchLocation.toReference().asString())

            css {
                // position:relative so the lip's bottom:0 anchors to THIS section's bottom, not the whole
                // construct's.
                position = Position.relative
            }

            branchStageTopShadow {
                div {
                    css {
                        // The rail is paint only (absolutely positioned over the stage's left edge), so this
                        // indent is the only thing holding the condition row and the step rows off the scope
                        // line — and it is what puts them in the same dependency-gutter column.
                        marginLeft = branchRailWidth
                        minHeight = 4.em
                    }

                    renderConditionRow(index, branchLocation, branchCount)

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

                    if (index == branchCount - 1) {
                        renderAddBranch()
                    }
                }
            }

            branchStageSectionLip()
        }
    }


    // The branch's own row: positional label, condition editor, remove and drag affordances. Routed through
    // scriptGutterRow so it registers in StepRowRefRegistry — the sole contract ScriptDependencyOverlay
    // anchors its polylines to, without which the dependency elbow into a condition silently vanishes — and so
    // its content lands in the identical left column as the step cards below it.
    //
    // Deliberately NOT marked data-step-header: ScriptExecutionMargin anchors on the If's OWN header row, and
    // its querySelector is document-first.
    private fun ChildrenBuilder.renderConditionRow(
        index: Int,
        branchLocation: ObjectLocation,
        branchCount: Int
    ) {
        scriptGutterRow(
            rowLocation = branchLocation,
            registry = stepRowRefRegistry(),
            gutter = {
                // Index 0 of a single-row list: the branch's only edges come from outside it, so this renders
                // the phantom target marker the overlay's polyline terminates in.
                stepDependencyGutterCellForStep(0, state.branchEdges?.get(branchLocation)
                    ?: StepDependencyEdges.EMPTY)
            },
            body = {
                div {
                    css {
                        position = Position.relative
                        paddingTop = 0.75.em

                        "&:hover > [data-drag-handle]" {
                            opacity = number(1.0)
                        }
                    }

                    onDragOver = { event -> onBranchDragOver(index, event) }
                    onDrop = { event -> onBranchDrop(index, event) }

                    // Only offered once there is something to reorder.
                    if (branchCount > 1) {
                        dragHandle(
                            isVisible = state.branchDragIndex == index,
                            handleColor = branchDragHandleColor,
                            onStart = { onBranchDragStart(index) },
                            onEnd = { onBranchDragEnd() })
                    }

                    dropIndicator(dropMarkerFor(
                        state.branchDragIndex,
                        state.branchDragOverIndex,
                        state.branchDropAfter ?: false,
                        index))

                    div {
                        css {
                            display = Display.flex
                            alignItems = AlignItems.center
                        }

                        div {
                            css {
                                fontWeight = FontWeight.bold
                                color = branchLabelColor
                                marginRight = 0.5.em
                                flexShrink = number(0.0)
                            }
                            // Positional, never the branch object's name — the name is stable identity only.
                            +(if (index == 0) "If" else "Else if")
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
            })
    }


    // Emitted inside an already-indented container (the last branch's body, or the zero-branch fallback's own
    // indent), so it adds none of its own.
    private fun ChildrenBuilder.renderAddBranch() {
        div {
            css {
                paddingTop = 0.25.em
                paddingBottom = 0.5.em
            }

            Button {
                size = Size.small

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


    // Always last, and always mounted even when empty: ScriptBranchDisplay is what accepts a toolbar insertion
    // or a step dropped into Else, so a "render nothing when empty" Else could never be filled. Empty, it is
    // dimmed to a ghost so the chain reads as ending at the last condition branch.
    private fun ChildrenBuilder.renderElse() {
        val ghost = state.elseEmpty ?: false

        div {
            css {
                marginLeft = branchRailWidth
                minHeight = if (ghost) 2.em else 4.em
                if (ghost) {
                    opacity = number(0.55)
                }
            }

            div {
                css {
                    // Clears the seam above; the step list reserves its own 32px below, so no bottom padding.
                    paddingTop = 0.75.em
                    fontWeight = FontWeight.bold
                    color = branchLabelColor
                }
                +"Else"
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
}
