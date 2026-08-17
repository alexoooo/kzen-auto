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
import tech.kzen.auto.client.wrap.RunProgressColors
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
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
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
    var visualFlowModel: VisualFlowModel
    var flowMatrix: FlowMatrix
    var flowDag: FlowDag

    // Routing, derived once per render by FlowController and threaded through — never re-derived per cell.
    var edgeRouting: FlowEdgeRouting
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


    //-----------------------------------------------------------------------------------------------------------------
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


    private fun egressColor(
        edgeDirection: EdgeDirection,
        isRunning: Boolean,
        hasMessage: Boolean
    ): Color {
        val routing = props.edgeRouting
        val nextToRun = routing.nextToRun
        if (nextToRun == null || !hasMessage) {
            return NamedColor.white
        }

        val nextCoordinate = props.cellDescriptor.coordinate.offset(edgeDirection)

        val isSending = isEgressActive(
            edgeDirection, nextCoordinate, nextToRun, routing.edgesLeadingToNextToRun)

        val isInFlight = isEgressActive(
            edgeDirection, nextCoordinate, nextToRun, routing.edgesInFlightToPending)

        val isEdgeMessageAvailable = isEgressAvailable(
            edgeDirection, nextCoordinate, routing.edgesAvailableToPending, routing.pendingWithAvailableMessage)

        val isCarrying = isEgressCarrying(
            edgeDirection, nextCoordinate, routing.edgesCarryingMessage)

        return when {
            isSending ->
                if (isRunning) RunProgressColors.goldSendingWhileRunning
                else NamedColor.gold

            isInFlight ->
                RunProgressColors.goldLight50

            isEdgeMessageAvailable ->
                RunProgressColors.goldLight90

            // Already-traversed upstream of the active path: between gold and white.
            isCarrying ->
                RunProgressColors.goldLight50

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
        val orientation = props.cellDescriptor.orientation
        val routing = props.edgeRouting

        val isRunning = props.visualFlowModel.isRunning()

        val isEdgeSendingMessage = props.cellDescriptor in routing.edgesLeadingToNextToRun
        val isEdgeInFlightMessage = props.cellDescriptor in routing.edgesInFlightToPending
        val isEdgeMessageAvailable = props.cellDescriptor in routing.edgesAvailableToPending
        val isEdgeCarryingMessage =
            routing.nextToRun != null && props.cellDescriptor in routing.edgesCarryingMessage

        val hasMessage = isEdgeSendingMessage || isEdgeInFlightMessage ||
                isEdgeMessageAvailable || isEdgeCarryingMessage

        val ingressAndCentreColor = when {
            isEdgeSendingMessage ->
                if (isRunning) {
                    RunProgressColors.goldSendingWhileRunning
                }
                else {
                    NamedColor.gold
                }

            isEdgeInFlightMessage ->
                RunProgressColors.goldLight50

            isEdgeMessageAvailable ->
                RunProgressColors.goldLight90

            // Already-traversed upstream of the active path: between gold and white.
            isEdgeCarryingMessage ->
                RunProgressColors.goldLight50

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

                orientation.hasLeftEgress() ->
                    renderEgressLeft(egressColor(EdgeDirection.Left, isRunning, hasMessage))

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
                renderEgressRight(egressColor(EdgeDirection.Right, isRunning, hasMessage))
            }
            else if (orientation.hasRightIngress()) {
                renderIngressRight(ingressAndCentreColor)
            }
        }

        if (orientation.hasBottom()) {
            val bottomColor = egressColor(EdgeDirection.Bottom, isRunning, hasMessage)
            BottomEgress::class.react {
                egressColor = bottomColor
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
