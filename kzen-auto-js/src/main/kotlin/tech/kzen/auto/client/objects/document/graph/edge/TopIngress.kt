package tech.kzen.auto.client.objects.document.graph.edge

import web.cssom.*
import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.graph.CellController
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.lib.common.model.attribute.AttributeName


//---------------------------------------------------------------------------------------------------------------------
external interface TopIngressProps: react.Props {
    var attributeName: AttributeName?
    var ingressColor: Color
    var parentWidth: Length?
}


//---------------------------------------------------------------------------------------------------------------------
class TopIngress(
    props: TopIngressProps
):
    RPureComponent<TopIngressProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val arrowPullBack: Length = 3.em.unaryMinus()
//            (CellController.arrowSide.plus(CellController.cardHorizontalMargin)).unaryMinus()
//            (-3).em
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val parentWidth = props.parentWidth ?: CellController.cardWidth
        val halfWidth = parentWidth.div(2)

        div {
            css {
                width = halfWidth.plus(CellController.arrowSide)
                height = CellController.ingressLength
            }

            div {
                css {
                    width = 0.px
                    height = 0.px

                    borderTop = Border(CellController.arrowSide, LineStyle.solid, props.ingressColor)
                    borderLeft = Border(CellController.arrowSide, LineStyle.solid, NamedColor.transparent)
                    borderRight = Border(CellController.arrowSide, LineStyle.solid, NamedColor.transparent)

                    float = Float.right
                }
            }

            div {
                css {
                    backgroundColor = props.ingressColor

                    width = CellController.arrowSide
                    height = CellController.arrowSide
//                    marginRight = (CellController.arrowSide.plus(CellController.cardHorizontalMargin)).unaryMinus()
                    marginRight = arrowPullBack
//                    marginRight = 3.em.unaryMinus()

                    float = Float.right
                }
            }

            val attributeName = props.attributeName
            if (attributeName != null &&
                    attributeName != DataflowUtils.mainInputAttributeName
            ) {
                div {
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