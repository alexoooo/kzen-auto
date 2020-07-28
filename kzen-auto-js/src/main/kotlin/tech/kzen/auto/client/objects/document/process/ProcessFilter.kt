package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.FilterListIcon
import tech.kzen.auto.client.wrap.reactStyle


class ProcessFilter(
    props: Props
):
    RPureComponent<ProcessFilter.Props, ProcessFilter.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState
    ): RProps


    class State: RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
                marginTop = 5.px
            }

            child(TopIngress::class) {
                attrs {
                    ingressColor = Color.white
                    parentWidth = 100.pct
                }
            }

            styledDiv {
                css {
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct
                }

                styledDiv {
                    css {
                        padding(0.5.em)
                    }

                    renderContent()
                }
            }

            child(BottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderFilters()
    }


    private fun RBuilder.renderHeader() {
        styledDiv {
            child(FilterListIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.75.em
                        marginRight = 0.25.em
                    }
                }
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"Filter"
            }
        }
    }


    private fun RBuilder.renderFilters() {
        styledDiv {
            +"..."

            val columnListing = props.processState.columnListing
            if (columnListing != null) {
                for (columnName in columnListing) {
                    styledDiv {
                        key = columnName
                        +columnName
                    }
                }
            }
        }
    }
}