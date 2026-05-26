package tech.kzen.auto.client.objects.document.script.display

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
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftInAttributeCommand
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface StepListDisplayProps: Props {
    var attributeLocation: AttributeLocation
    var nested: Boolean

    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
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
    override fun StepListDisplayState.init(props: StepListDisplayProps) {
        creating = false
        dropAfter = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        ClientContext.insertionGlobal.subscribe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.insertionGlobal.unsubscribe(this)
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure: GraphStructure = clientState.graphDefinitionAttempt.graphStructure

        if (props.attributeLocation.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val stepLocations = ScriptController.stepLocations(
            graphStructure, props.attributeLocation)

        val dependencyEdges = stepLocations?.let {
            StepDependencyEdges.compute(it, clientState.graphDefinitionAttempt)
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
        val graphStructure = ClientContext.clientStateGlobal.current()?.graphStructure()
            ?: return

        val archetypeObjectLocation = ClientContext.insertionGlobal
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
                ClientContext.mirroredGraphStore.apply(command)
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
            ClientContext.mirroredGraphStore.apply(ShiftInAttributeCommand(
                props.attributeLocation.objectLocation,
                sourceAttributePath,
                PositionRelation.at(newIndex)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val stepLocations = state.stepLocations
            ?: return

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

                firstOrLastInsertionPoint(0)
            }
        }
        else {
            div {
                nonEmptySteps(stepLocations)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.nonEmptySteps(
        stepLocations: List<ObjectLocation>
    ) {
        firstOrLastInsertionPoint(0)

        val edges = state.dependencyEdges
            ?: StepDependencyEdges.EMPTY

        div {
            for ((index, stepLocation) in stepLocations.withIndex()) {
                renderRowWithGutter(
                    gutter = { stepDependencyGutterCellForStep(index, edges) },
                    body = { renderStep(index, stepLocation, stepLocations.size) })

                if (index < stepLocations.size - 1) {
                    renderRowWithGutter(
                        gutter = { stepDependencyGutterCellForBetween(index, edges) },
                        body = { betweenStepsInsertionPoint(index + 1) })
                }
            }
        }

        firstOrLastInsertionPoint(stepLocations.size)
    }


    private fun ChildrenBuilder.renderRowWithGutter(
        gutter: ChildrenBuilder.() -> Unit,
        body: ChildrenBuilder.() -> Unit
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.stretch
            }
            gutter()
            div {
                css {
                    width = ScriptController.stepWidth
                    flexShrink = number(0.0)
                }
                body()
            }
        }
    }


    private fun ChildrenBuilder.betweenStepsInsertionPoint(index: Int) {
        div {
            css {
                position = Position.relative
                height = 1.5.em
                width = 100.pct
                borderTopWidth = 1.px
                borderTopStyle = LineStyle.solid
                borderTopColor = Color("rgba(0, 0, 0, 0.08)")
            }

            div {
                css {
                    position = Position.absolute
                    top = (-12).px
                    left = 50.pct
                    marginLeft = (-12).px
                }

                insertionButton(index)
            }
        }
    }


    private fun ChildrenBuilder.firstOrLastInsertionPoint(index: Int) {
        div {
            css {
                height = 26.px
                marginTop = 2.px
            }

            insertionButton(index)
        }
    }


    private fun ChildrenBuilder.insertionButton(index: Int) {
        if (!state.creating) {
            return
        }

        IconButton {
            title = "Insert step here"

            css {
                width = 24.px
                height = 24.px
                padding = 0.px
                backgroundColor = NamedColor.white

                hover {
                    backgroundColor = NamedColor.white
                }
            }

            onClick = {
                onCreate(index)
            }

            iconByName("AddCircleOutline") {
                style = unsafeJso {
                    fontSize = 1.em
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

            this.onDragStart = { onDragStart(index) }
            this.onDragOver = { event -> onDragOver(index, event) }
            this.onDrop = { event -> this@ScriptBranchDisplay.onDrop(event) }
            this.onDragEnd = { this@ScriptBranchDisplay.onDragEnd() }
        }
    }
}
