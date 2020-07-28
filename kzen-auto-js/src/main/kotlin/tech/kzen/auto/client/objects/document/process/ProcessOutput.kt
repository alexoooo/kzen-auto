package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.SaveAltIcon
import tech.kzen.auto.client.wrap.reactStyle


class ProcessOutput(
    props: Props
):
    RPureComponent<ProcessOutput.Props, ProcessOutput.State>(props)
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
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderOutput()
    }


    private fun RBuilder.renderHeader() {
        styledDiv {
            child(SaveAltIcon::class) {
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

                +"Output"
            }
        }
    }


    private fun RBuilder.renderOutput() {
        styledDiv {
            +"..."
        }
    }
}