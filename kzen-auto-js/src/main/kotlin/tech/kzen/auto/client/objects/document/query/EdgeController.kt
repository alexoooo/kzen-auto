package tech.kzen.auto.client.objects.document.query

import kotlinx.css.*
import kotlinx.css.properties.borderBottom
import kotlinx.css.properties.borderLeft
import kotlinx.css.properties.borderRight
import kotlinx.css.properties.borderTop
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.query.edge.BottomEgress
import tech.kzen.auto.client.objects.document.query.edge.TopIngress
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.DeleteIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.CellCoordinate
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.VertexDescriptor
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.RemoveInAttributeCommand


class EdgeController(
        props: Props
):
        RPureComponent<EdgeController.Props, EdgeController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var cellDescriptor: EdgeDescriptor,

            var documentPath: DocumentPath,
            var attributeNesting: AttributeNesting,
            var graphStructure: GraphStructure,
            var visualDataflowModel: VisualDataflowModel,
            var dataflowMatrix: DataflowMatrix,
            var dataflowDag: DataflowDag
    ): RProps


    class State(
            var edgeHover: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
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
                    QueryDocument.edgesAttributeName,
                    props.attributeNesting)

            ClientContext.commandBus.apply(RemoveInAttributeCommand(
                    sourceMain, objectAttributePath))
        }
    }


    private fun nextToRun(): ObjectLocation? {
        return props.visualDataflowModel.running()
                ?: DataflowUtils.next(
                        props.documentPath,
                        props.graphStructure,
                        props.visualDataflowModel)
    }


    private fun edgesSendingMessages(
            nextToRun: ObjectLocation
    ): Set<EdgeDescriptor> {
        // NB: might be null when navigating to new document while running
        @Suppress("MapGetWithNotNullAssertionOperator")
        val nextToRunVertexDescriptor = props.dataflowMatrix.verticesByLocation[nextToRun]
                ?: return setOf()

        val edgesLeadingToNextToRun = edgesLeadingTo(nextToRunVertexDescriptor)

        val edgesFlowingToPending = edgesFlowingToPending(
                nextToRunVertexDescriptor/*, edgesLeadingToNextToRun*/)

        return edgesLeadingToNextToRun + edgesFlowingToPending
    }


    private fun edgesFlowingToPending(
            nextToRun: VertexDescriptor
    ): Set<EdgeDescriptor> {
        val builder = mutableSetOf<EdgeDescriptor>()

        for ((objectLocation, vertexVisualModel) in props.visualDataflowModel.vertices) {
            if (vertexVisualModel.message == null) {
                continue
            }

            val pendingSuccessors = pendingSuccessors(objectLocation)
            if (nextToRun.objectLocation in pendingSuccessors) {
                continue
            }

            for (pendingSuccessor in pendingSuccessors) {
                val successorVertexDescriptor = props.dataflowMatrix.verticesByLocation[pendingSuccessor]
                        ?: throw IllegalStateException()

                val edgesToSuccessor = edgesLeadingTo(successorVertexDescriptor)

                val successorsUpToNextToRun = edgesToSuccessor
                        .filter { it.coordinate.row <= nextToRun.coordinate.row }

                builder.addAll(successorsUpToNextToRun)
            }
        }

        return builder
    }


    private fun pendingSuccessors(
            objectLocation: ObjectLocation
    ): List<ObjectLocation> {
        val successors = props.dataflowDag.successors[objectLocation]
                ?: return listOf()

        val builder = mutableListOf<ObjectLocation>()

        for (successor in successors) {
            val successorVisualVertexModel =
                    props.visualDataflowModel.vertices[successor]
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
            val sourceVertex = props.dataflowMatrix.traceVertexBackFrom(nextToRun, inputName)
                    ?: continue

            val sourceVisualModel = props.visualDataflowModel.vertices[sourceVertex.objectLocation]
                    ?: continue

            if (sourceVisualModel.message == null) {
                continue
            }

            val leadingEdges = props.dataflowMatrix.traceEdgeBackFrom(nextToRun, i)
            buffer.addAll(leadingEdges)
        }
        return buffer
    }


    fun isEgressActive(
            nextToRun: ObjectLocation,
            edgesLeadingToNextToRun: Set<EdgeDescriptor>,
            coordinate: CellCoordinate
    ): Boolean {
        if (edgesLeadingToNextToRun.any { it.coordinate == coordinate }) {
            return true
        }

        return nextToRun ==
                (props.dataflowMatrix.get(coordinate) as? VertexDescriptor)?.objectLocation
    }


    fun egressColor(
            nextToRun: ObjectLocation?,
            edgesLeadingToNextToRun: Set<EdgeDescriptor>,
            rowOffset: Int,
            columnOffset: Int
    ): Color {
        if (nextToRun == null) {
            return Color.white
        }

        val coordinate = props.cellDescriptor.coordinate.offset(rowOffset, columnOffset)
        val isActive = isEgressActive(nextToRun, edgesLeadingToNextToRun, coordinate)
        return (
            if (isActive) {
                Color.gold
            }
            else {
                Color.white
            }
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                width = CellController.cardWidth

                height = 100.pct
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver()
                }

                onMouseOutFunction = {
                    onMouseOut()
                }
            }

            renderEdge()
        }
    }


    private fun RBuilder.renderEdge() {
        val orientation = props.cellDescriptor.orientation

        val nextToRun = nextToRun()
        val edgesSendingMessages = nextToRun
                ?.let { edgesSendingMessages(nextToRun) }
                ?: setOf()

        val isEdgeSendingMessage =
                props.cellDescriptor in edgesSendingMessages

        val ingressAndCentreColor =
                if (isEdgeSendingMessage) {
                    Color.gold
                }
                else {
                    Color.white
                }

        if (orientation.hasTop()) {
            child(TopIngress::class) {
                attrs {
                    ingressColor = ingressAndCentreColor
                }
            }
        }
        else {
            styledDiv {
                css {
                    height = CellController.ingressLength
                }
            }
        }

        styledDiv {
            css {
                width = CellController.cardWidth
                marginBottom = (-5).px
            }

            when {
                orientation.hasLeftIngress() ->
                    renderIngressLeft(ingressAndCentreColor)

                orientation.hasLeftEgress() -> {
                    val edgeColor = egressColor(nextToRun, edgesSendingMessages, 0, -1)
                    renderEgressLeft(edgeColor)
                }

                else -> styledDiv {
                    css {
                        display = Display.inlineBlock
                        width = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
                        height = CellController.arrowSide
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    width = CellController.arrowSide
                    height = CellController.arrowSide

                    backgroundColor = ingressAndCentreColor
                }

                child(MaterialIconButton::class) {
                    attrs {
                        title = "Remove"

                        style = reactStyle {
                            marginTop = (-0.25).em
                            marginRight = (-8).px
                            float = Float.right

                            if (! state.edgeHover) {
                                visibility = Visibility.hidden
                            }
                        }

                        onClick = ::onRemove
                    }

                    child(DeleteIcon::class) {}
                }
            }

            if (orientation.hasRightEgress()) {
                val edgeColor = egressColor(nextToRun, edgesSendingMessages, 0, 1)
                renderEgressRight(edgeColor)
            }
            else if (orientation.hasRightIngress()) {
                renderIngressRight(ingressAndCentreColor)
            }
        }

        if (orientation.hasBottom()) {
            val edgeColor = egressColor(nextToRun, edgesSendingMessages, 1, 0)
            child(BottomEgress::class) {
                attrs {
                    egressColor = edgeColor
                }
            }
        }
        else {
            styledDiv {
                css {
                    height = CellController.egressLength
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderEgressLeft(
            cardColor: Color
    ) {
        styledDiv {
            css {
                display = Display.inlineBlock
                marginTop = CellController.cardHorizontalMargin.unaryMinus()
            }

            styledDiv {
                css {
                    width = 0.px
                    height = 0.px

                    borderRight(CellController.arrowSide, BorderStyle.solid, cardColor)
                    borderTop(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                    borderBottom(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                }
            }

            styledDiv {
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


    private fun RBuilder.renderEgressRight(
            cardColor: Color
    ) {
        styledDiv {
            css {
                display = Display.inlineBlock
                backgroundColor = cardColor

                width = CellController.cardWidth.div(2).minus(CellController.egressLength)

                height = 2.em
            }
        }

        styledDiv {
            css {
                display = Display.inlineBlock

                width = 0.px
                height = 0.px

                borderLeft(2.em, BorderStyle.solid, cardColor)
                borderTop(2.em, BorderStyle.solid, Color.transparent)
                borderBottom(2.em, BorderStyle.solid, Color.transparent)

                marginTop = (-1).em
                marginBottom = (-1).em
            }
        }
    }


    private fun RBuilder.renderIngressLeft(
            cardColor: Color
    ) {
        styledDiv {
            css {
                display = Display.inlineBlock
                marginTop = CellController.cardHorizontalMargin.unaryMinus()
            }

            styledDiv {
                css {
                    width = 0.px
                    height = 0.px

                    borderLeft(CellController.arrowSide, BorderStyle.solid, cardColor)
                    borderTop(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                    borderBottom(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                }
            }

            styledDiv {
                css {
                    backgroundColor = cardColor

                    width = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
                    height = CellController.arrowSide
                    marginTop = (-3).em
                }
            }
        }
    }


    private fun RBuilder.renderIngressRight(
            cardColor: Color
    ) {
        styledDiv {
            css {
                display = Display.inlineBlock
                backgroundColor = cardColor
//                backgroundColor = Color.burlyWood

                width = CellController.cardWidth.div(2)
                        .minus(CellController.arrowSide).plus(3.px)
                //.minus(CellController.egressLength)

                height = 2.em
            }
        }

        styledDiv {
            css {
                display = Display.inlineBlock

                width = 0.px
                height = 0.px

                borderRight(2.em, BorderStyle.solid, cardColor)
                borderTop(2.em, BorderStyle.solid, Color.transparent)
                borderBottom(2.em, BorderStyle.solid, Color.transparent)

                marginTop = (-3).em.minus(3.px)
                marginBottom = (-1).em
                float = Float.right
            }
        }
    }
}