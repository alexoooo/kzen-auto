package tech.kzen.auto.client.objects.document.graph.edge

import kotlinx.css.*
import kotlinx.css.properties.borderLeft
import kotlinx.css.properties.borderRight
import kotlinx.css.properties.borderTop
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.graph.CellController
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.attribute.AttributeName


class TopIngress(
        props: Props
):
        RPureComponent<TopIngress.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeName: AttributeName?,
            var ingressColor: Color
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                width = CellController.cardWidth.div(2).plus(CellController.arrowSide)
                height = CellController.ingressLength
            }

            styledDiv {
                css {
                    width = 0.px
                    height = 0.px

                    borderTop(CellController.arrowSide, BorderStyle.solid, props.ingressColor)
                    borderLeft(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                    borderRight(CellController.arrowSide, BorderStyle.solid, Color.transparent)

                    float = Float.right
                }
            }

            styledDiv {
                css {
                    backgroundColor = props.ingressColor

                    width = CellController.arrowSide
                    height = CellController.arrowSide//.plus(1.px)
                    marginRight = (CellController.arrowSide.plus(CellController.cardHorizontalMargin)).unaryMinus()

                    float = Float.right
                }
            }

            val attributeName = props.attributeName
            if (attributeName != null &&
                    attributeName != DataflowUtils.mainInputAttributeName) {
                styledDiv {
                    css {
                        float = Float.right
                        height = 1.em
                        marginTop = 0.5.em
                    }

                    +attributeName.value
                }
            }
        }
    }
}