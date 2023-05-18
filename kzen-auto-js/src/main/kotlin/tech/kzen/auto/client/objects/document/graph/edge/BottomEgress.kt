package tech.kzen.auto.client.objects.document.graph.edge

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.graph.CellController
import tech.kzen.auto.client.wrap.RPureComponent
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface BottomEgressProps: react.Props {
    var egressColor: Color
    var parentWidth: Length?
}


//---------------------------------------------------------------------------------------------------------------------
class BottomEgress(
    props: BottomEgressProps
):
    RPureComponent<BottomEgressProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val parentWidth = props.parentWidth ?: CellController.cardWidth
        val halfWidth = parentWidth.div(2)

        div {
            css {
                height = CellController.egressLength
            }
        }

        div {
            css {
                position = Position.absolute

                width = CellController.arrowSide

                bottom = 2.em
                top = 3.em
                left = halfWidth.minus(1.em)
                zIndex = integer(-2)

                backgroundColor = props.egressColor
            }
        }

        div {
            css {
                position = Position.absolute
                bottom = 0.em

//                width = halfWidth.plus(CellController.arrowSide)
//                width = 100.pct.plus(CellController.arrowSide)
                width = 100.pct
                height = CellController.egressLength
                zIndex = integer(-1)
            }

            div {
                css {
                    backgroundColor = props.egressColor

                    width = CellController.arrowSide
                    height = CellController.arrowSide.div(2)

                    marginLeft = halfWidth.minus(CellController.cardHorizontalMargin)
                }
            }

            div {
                css {
                    width = 0.px
                    height = 0.px

                    borderTop = Border(CellController.arrowSide, LineStyle.solid, props.egressColor)
                    borderLeft = Border(CellController.arrowSide, LineStyle.solid, NamedColor.transparent)
                    borderRight = Border(CellController.arrowSide, LineStyle.solid, NamedColor.transparent)

                    marginLeft = halfWidth.minus(CellController.arrowSide)
                }
            }
        }
    }
}