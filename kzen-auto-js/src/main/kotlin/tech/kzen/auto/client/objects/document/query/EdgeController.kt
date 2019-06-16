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
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.model.structure.cell.EdgeDescriptor
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
            var dataflowMatrix: DataflowMatrix
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


    private fun isEdgePredecessorOfNextToRun(): Boolean {
        val flowTarget = props.visualDataflowModel.running()
                ?: DataflowUtils.next(
                        props.documentPath,
                        props.graphStructure,
                        props.visualDataflowModel)
                ?: return false

        @Suppress("MapGetWithNotNullAssertionOperator")
        val targetVertexDescriptor = props.dataflowMatrix.verticesByLocation[flowTarget]!!

        for ((i, inputName) in targetVertexDescriptor.inputNames.withIndex()) {
            val sourceVertex = props.dataflowMatrix.traceVertexBackFrom(targetVertexDescriptor, inputName)
                    ?: continue

            val sourceVisualModel = props.visualDataflowModel.vertices[sourceVertex.objectLocation]
                    ?: continue

            if (sourceVisualModel.message == null) {
                continue
            }

            val leadingEdges = props.dataflowMatrix.traceEdgeBackFrom(targetVertexDescriptor, i)
            if (leadingEdges.contains(props.cellDescriptor)) {
                return true
            }
        }

        return false
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

        val edgeColor =
                if (isEdgePredecessorOfNextToRun()) {
                    Color.gold
                }
                else {
                    Color.white
                }

        if (orientation.hasTop()) {
            child(TopIngress::class) {
                attrs {
                    ingressColor = edgeColor
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
                    renderIngressLeft(edgeColor)

                orientation.hasLeftEgress() ->
                    renderEgressLeft(edgeColor)

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

                    backgroundColor = edgeColor
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
                renderEgressRight(edgeColor)
            }
            else if (orientation.hasRightIngress()) {
                renderIngressRight(edgeColor)
            }
        }

        if (orientation.hasBottom()) {
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