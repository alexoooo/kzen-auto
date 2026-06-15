package tech.kzen.auto.client.objects.document.script.display.dependency

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.dragdrop.computeDropIndex
import tech.kzen.auto.client.objects.document.common.dragdrop.dropMarkerFor
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepSlot
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.display.image.StepImageThumbnail
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftInAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.NamedColor
import web.cssom.em
import web.cssom.number
import web.cssom.pct
import web.cssom.px
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepListDisplayProps: Props {
    var attributeLocation: AttributeLocation
    var nested: Boolean

    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander

    var clientStateGlobal: ClientStateGlobal
    var insertionGlobal: InsertionGlobal
    var mirroredGraphStore: MirroredGraphStore
    var objectStableMapper: ObjectStableMapper
}


external interface StepListDisplayState: State {
    var stepLocations: List<ObjectLocation>?
    var dependencyEdges: StepDependencyEdges?

    var creating: Boolean

    var dragSourceIndex: Int?
    var dragOverIndex: Int?
    var dropAfter: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptBranchDisplay(
    props: StepListDisplayProps
):
    RPureComponent<StepListDisplayProps, StepListDisplayState>(props),
    ClientStateGlobal.Observer,
    InsertionGlobal.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val dragHandleColor = Color("rgba(0, 0, 0, 0.45)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Constructed once per instance, so these are ===-stable across renders and let each ScriptStepSlot
    // (RPureComponent) bail when only a sibling changed. The slot threads its own indexInParent back in,
    // so a single shared reference serves every slot rather than a fresh closure per slot per render.
    private val onSlotDragStart: (Int) -> Unit = { index -> onDragStart(index) }
    private val onSlotDragOver: (Int, DragEvent<HTMLDivElement>) -> Unit = { index, event -> onDragOver(index, event) }
    private val onSlotDrop: (DragEvent<HTMLDivElement>) -> Unit = { event -> onDrop(event) }
    private val onSlotDragEnd: () -> Unit = { onDragEnd() }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepListDisplayState.init(props: StepListDisplayProps) {
        creating = false
        dropAfter = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        props.insertionGlobal.subscribe(this)
    }


    override fun componentWillUnmount() {
        props.insertionGlobal.unsubscribe(this)
        props.clientStateGlobal.unobserve(this)
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

        val archetypeObjectLocation = props.insertionGlobal
            .getAndClearSelection()
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
        setState {
            dragSourceIndex = sourceIndex
            dragOverIndex = null
            dropAfter = false
        }
    }


    private fun onDragOver(targetIndex: Int, event: DragEvent<HTMLDivElement>) {
        event.preventDefault()

        if (state.dragSourceIndex == null) {
            return
        }

        val rect = event.currentTarget.getBoundingClientRect()
        val nextDropAfter = event.clientY > rect.top + rect.height / 2

        if (state.dragOverIndex == targetIndex && state.dropAfter == nextDropAfter) {
            return
        }

        setState {
            dragOverIndex = targetIndex
            dropAfter = nextDropAfter
        }
    }


    private fun onDragEnd() {
        if (state.dragSourceIndex == null && state.dragOverIndex == null) {
            return
        }
        setState {
            dragSourceIndex = null
            dragOverIndex = null
            dropAfter = false
        }
    }


    private fun onDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()

        val source = state.dragSourceIndex
        val target = state.dragOverIndex
        val dropAfterValue = state.dropAfter

        setState {
            dragSourceIndex = null
            dragOverIndex = null
            dropAfter = false
        }

        if (source == null || target == null) {
            return
        }
        val newIndex = computeDropIndex(source, target, dropAfterValue)
        if (newIndex == source) {
            return
        }

        val sourceAttributePath = AttributePath(
            props.attributeLocation.attributePath.attribute,
            props.attributeLocation.attributePath.nesting.push(AttributeSegment.ofIndex(source)))

        async {
            props.mirroredGraphStore.apply(ShiftInAttributeCommand(
                props.attributeLocation.objectLocation,
                sourceAttributePath,
                PositionRelation.at(newIndex)))
        }
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

            if (stepLocations.isEmpty()) {
                div {
                    css {
                        paddingTop = 2.em
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

                    firstOrLastInsertionPoint(0, StepDependencyEdges.EMPTY)
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
                ref = refCallback<HTMLDivElement> { element ->
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
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                height = 1.5.em
                width = 100.pct
            }

            insertionButton(index)
        }
    }


    private fun ChildrenBuilder.firstOrLastInsertionPoint(index: Int, edges: StepDependencyEdges) {
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
                div {
                    css {
                        height = 30.px
                        marginTop = 2.px
                    }

                    insertionButton(index)
                }
            })
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
        val marker = dropMarkerFor(
            state.dragSourceIndex, state.dragOverIndex, state.dropAfter, index)

        ScriptStepSlot::class.react {
            key = Key(objectLocation.toReference().asString())

            this.objectLocation = objectLocation
            this.indexInParent = index
            this.first = index == 0
            this.last = index == stepCount - 1

            this.dropMarker = marker
            this.isDragSource = state.dragSourceIndex == index

            this.stepDisplayManager = props.stepDisplayManager
            this.handleColor = dragHandleColor

            this.onDragStart = onSlotDragStart
            this.onDragOver = onSlotDragOver
            this.onDrop = onSlotDrop
            this.onDragEnd = onSlotDragEnd
        }
    }
}
