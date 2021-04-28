package tech.kzen.auto.client.objects.document.report.edge

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


class ReportBottomEgress(
        props: Props
):
        RPureComponent<ReportBottomEgress.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var egressColor: Color,
            var parentWidth: LinearDimension?
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val parentWidth = props.parentWidth ?: CellController.cardWidth
        val halfWidth = parentWidth.div(2)

        styledDiv {
            css {
//                height = CellController.egressLength
                height = 1.5.em
//                marginBottom = 0.5.em
                marginBottom = 1.em
            }

//            child(ArrowDownwardIcon::class) {}
        }

//        styledDiv {
//            css {
//                position = Position.absolute
//
//                width = CellController.arrowSide
//
//                bottom = 2.em
//                top = 3.em
//                left = halfWidth.minus(1.em)
//                zIndex = -2
//
//                backgroundColor = props.egressColor
//            }
//        }

        styledDiv {
            css {
                position = Position.absolute
                bottom = 0.em

//                width = halfWidth.plus(CellController.arrowSide)
//                width = 100.pct.plus(CellController.arrowSide)
                width = 100.pct
                height = CellController.egressLength
                zIndex = -1
            }

            styledDiv {
                css {
                    backgroundColor = props.egressColor

                    width = CellController.arrowSide
                    height = CellController.arrowSide.div(2)

                    marginLeft = halfWidth.minus(CellController.cardHorizontalMargin)
                }
            }

            styledDiv {
                css {
                    width = 0.px
                    height = 0.px

                    borderTop(CellController.arrowSide, BorderStyle.solid, props.egressColor)
                    borderLeft(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                    borderRight(CellController.arrowSide, BorderStyle.solid, Color.transparent)

                    marginLeft = halfWidth.minus(CellController.arrowSide)
                }
            }
        }
    }
}