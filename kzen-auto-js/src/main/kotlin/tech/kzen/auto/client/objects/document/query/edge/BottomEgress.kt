package tech.kzen.auto.client.objects.document.query.edge

import kotlinx.css.*
import kotlinx.css.properties.borderLeft
import kotlinx.css.properties.borderRight
import kotlinx.css.properties.borderTop
import react.RBuilder
import react.RProps
import react.RState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.query.CellController
import tech.kzen.auto.client.wrap.RPureComponent


class BottomEgress(
        props: Props
):
        RPureComponent<BottomEgress.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var egressColor: Color
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                height = CellController.egressLength
            }
        }

        styledDiv {
            css {
                position = Position.absolute

                width = CellController.arrowSide

                bottom = 2.em
                top = 3.em
                left = CellController.cardWidth.div(2).minus(1.em)
                zIndex = -2

                backgroundColor = props.egressColor
            }
        }

        styledDiv {
            css {
                position = Position.absolute
                bottom = 0.em

                width = CellController.cardWidth.div(2).plus(CellController.arrowSide)
                height = CellController.egressLength
                zIndex = -1
            }

            styledDiv {
                css {
                    backgroundColor = props.egressColor

                    width = CellController.arrowSide
                    height = CellController.arrowSide.div(2)

                    marginLeft = CellController.cardWidth.div(2).minus(CellController.cardHorizontalMargin)
                }
            }

            styledDiv {
                css {
                    width = 0.px
                    height = 0.px

                    borderTop(CellController.arrowSide, BorderStyle.solid, props.egressColor)
                    borderLeft(CellController.arrowSide, BorderStyle.solid, Color.transparent)
                    borderRight(CellController.arrowSide, BorderStyle.solid, Color.transparent)

                    marginLeft = CellController.cardWidth.div(2).minus(CellController.arrowSide)
                }
            }
        }
    }
}