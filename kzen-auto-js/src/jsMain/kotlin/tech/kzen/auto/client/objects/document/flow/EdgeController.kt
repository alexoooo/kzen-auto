package tech.kzen.auto.client.objects.document.flow

import emotion.react.css
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.flow.edge.BottomEgress
import tech.kzen.auto.client.objects.document.flow.edge.TopIngress
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.EdgeDirection
import tech.kzen.auto.common.paradigm.flow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.paradigm.flow.util.FlowUtils
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface EdgeControllerProps: Props {
    var mirroredGraphStore: MirroredGraphStore

    var cellDescriptor: EdgeDescriptor

    var documentPath: DocumentPath
    var attributeNesting: AttributeNesting
    var graphStructure: GraphStructure
    var visualFlowModel: VisualFlowModel
    var flowMatrix: FlowMatrix
    var flowDag: FlowDag
}


external interface EdgeControllerState: State {
    var edgeHover: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class EdgeController(
    props: EdgeControllerProps
):
    RPureComponent<EdgeControllerProps, EdgeControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val goldLight20 = Color("#ffe13f")
        val goldLight25 = Color("#ffe13f")
        val goldLight50 = Color("#ffeb7f")
        val goldLight75 = Color("#fff5bf")
        val goldLight90 = Color("#fffbe5")
        val goldLight93 = Color("#fffced")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun EdgeControllerState.init(props: EdgeControllerProps) {
        edgeHover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver() {
        setState {
            edgeHover = true
        }
    }


    private fun onMouseOut() {
        setState {
            edgeHover = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove() {
        async {
            val sourceMain = ObjectLocation(
                    props.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    FlowConventions.edgesAttributeName,
                    props.attributeNesting)

            props.mirroredGraphStore.apply(RemoveInAttributeCommand(
                    sourceMain, objectAttributePath, false))
        }
    }


    private fun nextToRun(): ObjectLocation? {
        return props.visualFlowModel.running()
                ?: FlowUtils.next(
                        props.documentPath,
                        props.graphStructure,
                        props.visualFlowModel)
    }


    private fun pendingToRunVertexDescriptor(
            nextToRun: ObjectLocation
    ): VertexDescriptor? {
        val nextToRunVisualVertexModel = props.visualFlowModel.vertices[nextToRun]
                ?: return null

        if (nextToRunVisualVertexModel.epoch > 0) {
            return null
        }

        // NB: might be null when navigating to new document while running
        return props.flowMatrix.verticesByLocation[nextToRun]
    }


    private fun edgesFlowingToPending(
            nextToRun: VertexDescriptor
    ): Set<EdgeDescriptor> {
        val builder = mutableSetOf<EdgeDescriptor>()

        for ((objectLocation, vertexVisualModel) in props.visualFlowModel.vertices) {
            if (vertexVisualModel.message == null) {
                continue
            }

            val pendingSuccessors = pendingSuccessors(objectLocation)
            if (nextToRun.objectLocation in pendingSuccessors) {
                continue
            }

            for (pendingSuccessor in pendingSuccessors) {
                val successorVertexDescriptor = props.flowMatrix.verticesByLocation[pendingSuccessor]
                        ?: throw IllegalStateException()

                val edgesToSuccessor = edgesLeadingTo(successorVertexDescriptor)

                val successorsUpToNextToRun = edgesToSuccessor
                        .filter { it.coordinate.row <= nextToRun.coordinate.row }

                builder.addAll(successorsUpToNextToRun)
            }
        }

        return builder
    }


    private fun pendingWithAvailableMessage(): Set<ObjectLocation> {
        return props.visualFlowModel.vertices
                .filter { it.value.message != null }
                .flatMap { pendingSuccessors(it.key) }
                .toSet()
    }


    private fun edgesAvailableToPending(
            pendingWithAvailableMessage: Collection<ObjectLocation>
    ): Set<EdgeDescriptor> {
        return pendingWithAvailableMessage
                .mapNotNull { props.flowMatrix.verticesByLocation[it] }
                .flatMap { edgesLeadingTo(it) }
                .toSet()

//        val builder = mutableSetOf<EdgeDescriptor>()
//
//        for ((objectLocation, vertexVisualModel) in props.visualFlowModel.vertices) {
//            if (vertexVisualModel.message == null) {
//                continue
//            }
//
//            val pendingSuccessors = pendingSuccessors(objectLocation)
//
//            for (pendingSuccessor in pendingSuccessors) {
//                val successorVertexDescriptor = props.flowMatrix.verticesByLocation[pendingSuccessor]
//                        ?: throw IllegalStateException()
//
//                val edgesToSuccessor = edgesLeadingTo(successorVertexDescriptor)
//
//                builder.addAll(edgesToSuccessor)
//            }
//        }
//
//        return builder
    }


    private fun pendingSuccessors(
            objectLocation: ObjectLocation
    ): List<ObjectLocation> {
        val successors = props.flowDag.successors[objectLocation]
                ?: return listOf()

        val builder = mutableListOf<ObjectLocation>()

        for (successor in successors) {
            val successorVisualVertexModel =
                    props.visualFlowModel.vertices[successor]
                    ?: continue

            if (successorVisualVertexModel.epoch == 0) {
                builder.add(successor)
            }
        }

        return builder
    }


    private fun edgesLeadingTo(
            nextToRun: VertexDescriptor
    ): Set<EdgeDescriptor> {
        val buffer = mutableSetOf<EdgeDescriptor>()
        for ((i, inputName) in nextToRun.inputNames.withIndex()) {
            val sourceVertex = props.flowMatrix.traceVertexBackFrom(nextToRun, inputName)
                    ?: continue

            val sourceVisualModel = props.visualFlowModel.vertices[sourceVertex.objectLocation]
                    ?: continue

            if (sourceVisualModel.message == null) {
                continue
            }

            val leadingEdges = props.flowMatrix.traceEdgeBackFrom(nextToRun, i)
            buffer.addAll(leadingEdges)
        }
        return buffer
    }


    // Every edge currently carrying a message: leading to any vertex (pending or already-run) fed by a
    // source that still holds its message. Superset of the sending / in-flight / available sets; the
    // colour when-chains test those stronger categories first, so this only colours the "already
    // traversed" upstream segments of the active path — the message went down them but its consumer has
    // already run (epoch > 0), which the pending-only sets miss and leave white.
    private fun edgesCarryingMessage(): Set<EdgeDescriptor> {
        val builder = mutableSetOf<EdgeDescriptor>()
        for (vertexDescriptor in props.flowMatrix.verticesByLocation.values) {
            builder.addAll(edgesLeadingTo(vertexDescriptor))
        }
        return builder
    }


    private fun hasMessageHoldingPredecessor(
            vertexLocation: ObjectLocation
    ): Boolean {
        val predecessors = props.flowDag.predecessors[vertexLocation]
                ?: return false
        return predecessors.any { props.visualFlowModel.vertices[it]?.message != null }
    }


    private fun isEgressCarrying(
            edgeDirection: EdgeDirection,
            offsetCoordinate: CellCoordinate,
            edgesCarryingMessage: Set<EdgeDescriptor>
    ): Boolean {
        val carryingEdgeAtOffset = edgesCarryingMessage.find { it.coordinate == offsetCoordinate }
        if (carryingEdgeAtOffset != null) {
            return carryingEdgeAtOffset.orientation.hasIngress(edgeDirection.reverse())
        }

        val vertexDescriptor = props.flowMatrix.get(offsetCoordinate) as? VertexDescriptor
                ?: return false

        // Egress flowing down into a vertex that received a message (whether it has consumed it yet or not).
        return edgeDirection == EdgeDirection.Bottom &&
                hasMessageHoldingPredecessor(vertexDescriptor.objectLocation)
    }


    private fun isEgressActive(
            edgeDirection: EdgeDirection,
            nextCoordinate: CellCoordinate,
            nextToRun: ObjectLocation,
            edgesLeadingToActive: Set<EdgeDescriptor>
    ): Boolean {
        val activeEdgeAtOffset = edgesLeadingToActive.find { it.coordinate == nextCoordinate }
        if (activeEdgeAtOffset != null) {
            return activeEdgeAtOffset.orientation.hasIngress(edgeDirection.reverse())
        }

        val vertexDescriptor = props.flowMatrix.get(nextCoordinate) as? VertexDescriptor
                ?: return false

        return edgeDirection == EdgeDirection.Bottom &&
                nextToRun == vertexDescriptor.objectLocation &&
                props.visualFlowModel.vertices[nextToRun]?.epoch == 0
    }


    private fun isEgressAvailable(
            edgeDirection: EdgeDirection,
            offsetCoordinate: CellCoordinate,
            edgesLeadingToNextToRun: Set<EdgeDescriptor>,
            pendingWithAvailableMessage: Set<ObjectLocation>
    ): Boolean {
        val nextToRunEdgeAtOffset = edgesLeadingToNextToRun.find { it.coordinate == offsetCoordinate }
        if (nextToRunEdgeAtOffset != null) {
            return nextToRunEdgeAtOffset.orientation.hasIngress(edgeDirection.reverse())
        }

        val vertexDescriptor = props.flowMatrix.get(offsetCoordinate) as? VertexDescriptor
                ?: return false

        return edgeDirection == EdgeDirection.Bottom &&
                vertexDescriptor.objectLocation in pendingWithAvailableMessage
    }


    // TODO: refactor
    private fun egressColor(
            edgeDirection: EdgeDirection,
            isRunning: Boolean,
            nextToRun: ObjectLocation?,
            hasMessage: Boolean,
            edgesLeadingToNextToRun: Set<EdgeDescriptor>,
            edgesInFlightToPending: Set<EdgeDescriptor>,
            edgesAvailableToPending: Set<EdgeDescriptor>,
            edgesCarryingMessage: Set<EdgeDescriptor>,
            pendingWithAvailableMessage: Set<ObjectLocation>
    ): Color {
        if (nextToRun == null || !hasMessage) {
            return NamedColor.white
        }

        val nextCoordinate = props.cellDescriptor.coordinate.offset(edgeDirection)

        val isSending = isEgressActive(
                edgeDirection, nextCoordinate, nextToRun, edgesLeadingToNextToRun)

        val isInFlight = isEgressActive(
                edgeDirection, nextCoordinate, nextToRun, edgesInFlightToPending)

        val isEdgeMessageAvailable = isEgressAvailable(
                edgeDirection, nextCoordinate, edgesAvailableToPending, pendingWithAvailableMessage)

        val isCarrying = isEgressCarrying(
                edgeDirection, nextCoordinate, edgesCarryingMessage)

        return when {
            isSending ->
                if (isRunning) goldLight20
                else NamedColor.gold

            isInFlight ->
                goldLight50

            isEdgeMessageAvailable ->
                goldLight90

            // Already-traversed upstream of the active path: between gold and white.
            isCarrying ->
                goldLight50

            else ->
                NamedColor.white
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                filter = dropShadow(0.px, 0.px, 0.px, NamedColor.gray)
                width = CellController.cardWidth
                height = 100.pct
            }

            onMouseOver = {
                onMouseOver()
            }

            onMouseOut = {
                onMouseOut()
            }

            renderEdge()
        }
    }


    private fun ChildrenBuilder.renderEdge() {
//        val isDebug =
//                props.cellDescriptor.coordinate.row == 5 &&
//                props.cellDescriptor.coordinate.column == 0

        val orientation = props.cellDescriptor.orientation

        val isRunning = props.visualFlowModel.isRunning()
        val nextToRun = nextToRun()
        val pendingToRunVertexDescriptor = nextToRun?.let { pendingToRunVertexDescriptor(it) }

        val edgesLeadingToNextToRun = pendingToRunVertexDescriptor
                ?.let { edgesLeadingTo(it) }
                ?: setOf()

        val edgesInFlightToPending = pendingToRunVertexDescriptor
                ?.let { edgesFlowingToPending(it) }
                ?: setOf()

//        val edgesSendingMessages = edgesLeadingToNextToRun + edgesInFlightToPending

        val isEdgeSendingMessage = props.cellDescriptor in edgesLeadingToNextToRun
        val isEdgeInFlightMessage = props.cellDescriptor in edgesInFlightToPending

        val pendingWithAvailableMessage = pendingWithAvailableMessage()
        val edgesAvailableToPending = edgesAvailableToPending(pendingWithAvailableMessage)
        val isEdgeMessageAvailable = props.cellDescriptor in edgesAvailableToPending

        val edgesCarryingMessage = edgesCarryingMessage()
        val isEdgeCarryingMessage = nextToRun != null && props.cellDescriptor in edgesCarryingMessage

        val hasMessage = isEdgeSendingMessage || isEdgeInFlightMessage ||
                isEdgeMessageAvailable || isEdgeCarryingMessage

        val ingressAndCentreColor = when {
            isEdgeSendingMessage ->
                if (isRunning) {
                    goldLight25
                }
                else {
                    NamedColor.gold
                }

            isEdgeInFlightMessage ->
                goldLight50

            isEdgeMessageAvailable ->
                goldLight90

            // Already-traversed upstream of the active path: between gold and white.
            isEdgeCarryingMessage ->
                goldLight50

            else ->
                NamedColor.white
        }

        if (orientation.hasTop()) {
            TopIngress::class.react {
                ingressColor = ingressAndCentreColor
            }
        }
        else {
            div {
                css {
                    height = CellController.ingressLength
                }
            }
        }

        div {
            css {
                width = CellController.cardWidth
                marginBottom = (-5).px
            }

            when {
                orientation.hasLeftIngress() ->
                    renderIngressLeft(ingressAndCentreColor)

                orientation.hasLeftEgress() -> {
                    val edgeColor = egressColor(
                            EdgeDirection.Left,
                            isRunning,
                            nextToRun,
                            hasMessage,
                            edgesLeadingToNextToRun,
                            edgesInFlightToPending,
                            edgesAvailableToPending,
                            edgesCarryingMessage,
                            pendingWithAvailableMessage)
                    renderEgressLeft(edgeColor)
                }

                else -> div {
                    css {
                        display = Display.inlineBlock
                        width = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
                        height = CellController.arrowSide
                    }
                }
            }

            div {
                css {
                    display = Display.inlineBlock
                    width = CellController.arrowSide
                    height = CellController.arrowSide

                    backgroundColor = ingressAndCentreColor
                }

                IconButton {
                    sx {
                        marginTop = (-0.25).em
                        marginRight = (-8).px
                        float = Float.right

                        if (!state.edgeHover) {
                            visibility = Visibility.hidden
                        }
                    }

                    title = "Remove"

                    onClick = { onRemove() }

                    icon("material-symbols:delete") {}
                }
            }

            if (orientation.hasRightEgress()) {
                val edgeColor = egressColor(
                        EdgeDirection.Right,
                        isRunning,
                        nextToRun,
                        hasMessage,
                        edgesLeadingToNextToRun,
                        edgesInFlightToPending,
                        edgesAvailableToPending,
                        edgesCarryingMessage,
                        pendingWithAvailableMessage)
                renderEgressRight(edgeColor)
            }
            else if (orientation.hasRightIngress()) {
                renderIngressRight(ingressAndCentreColor)
            }
        }

        if (orientation.hasBottom()) {
            val edgeColor = egressColor(
                    EdgeDirection.Bottom,
                    isRunning,
                    nextToRun,
                    hasMessage,
                    edgesLeadingToNextToRun,
                    edgesInFlightToPending,
                    edgesAvailableToPending,
                    edgesCarryingMessage,
                    pendingWithAvailableMessage)
            BottomEgress::class.react {
                egressColor = edgeColor
            }
        }
        else {
            div {
                css {
                    height = CellController.egressLength
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderEgressLeft(
            cardColor: Color
    ) {
        div {
            css {
                display = Display.inlineBlock
                marginTop = CellController.cardHorizontalMargin.unaryMinus()
            }

            div {
                css {
                    width = 0.px
                    height = 0.px

                    borderRight = Border(CellController.arrowSide, LineStyle.solid, cardColor)
                    borderTop = Border(CellController.arrowSide, LineStyle.solid, Color.transparent)
                    borderBottom = Border(CellController.arrowSide, LineStyle.solid, Color.transparent)
                }
            }

            div {
                css {
                    backgroundColor = cardColor

                    width = CellController.cardWidth.div(2)
                            .minus(CellController.cardHorizontalMargin)
                            .minus(CellController.arrowSide)
                    height = CellController.arrowSide
                    marginTop = (-3).em
                    marginLeft = CellController.arrowSide
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEgressRight(
            cardColor: Color
    ) {
        div {
            css {
                display = Display.inlineBlock
                backgroundColor = cardColor

                width = CellController.cardWidth.div(2).minus(CellController.egressLength)

                height = 2.em
            }
        }

        div {
            css {
                display = Display.inlineBlock

                width = 0.px
                height = 0.px

                borderLeft = Border(2.em, LineStyle.solid, cardColor)
                borderTop = Border(2.em, LineStyle.solid, Color.transparent)
                borderBottom = Border(2.em, LineStyle.solid, Color.transparent)

                marginTop = (-1).em
                marginBottom = (-1).em
            }
        }
    }


    private fun ChildrenBuilder.renderIngressLeft(
            cardColor: Color
    ) {
        div {
            css {
                display = Display.inlineBlock
                marginTop = CellController.cardHorizontalMargin.unaryMinus()
            }

            div {
                css {
                    width = 0.px
                    height = 0.px

                    borderLeft = Border(CellController.arrowSide, LineStyle.solid, cardColor)
                    borderTop = Border(CellController.arrowSide, LineStyle.solid, Color.transparent)
                    borderBottom = Border(CellController.arrowSide, LineStyle.solid, Color.transparent)
                }
            }

            div {
                css {
                    backgroundColor = cardColor

                    width = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
                    height = CellController.arrowSide
                    marginTop = (-3).em
                }
            }
        }
    }


    private fun ChildrenBuilder.renderIngressRight(
            cardColor: Color
    ) {
        div {
            css {
                display = Display.inlineBlock
                backgroundColor = cardColor
//                backgroundColor = Color.burlyWood

                width = CellController.cardWidth.div(2)
                        .minus(CellController.arrowSide).plus(3.px)

                height = 2.em
            }
        }

        div {
            css {
                display = Display.inlineBlock

                width = 0.px
                height = 0.px

                borderRight = Border(2.em, LineStyle.solid, cardColor)
                borderTop = Border(2.em, LineStyle.solid, Color.transparent)
                borderBottom = Border(2.em, LineStyle.solid, Color.transparent)

                marginTop = (-3).em.minus(3.px)
                marginBottom = (-1).em
                float = Float.right
            }
        }
    }
}