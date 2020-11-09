package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.*
import styled.*
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.PageviewIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.process.OutputPreview


class ProcessOutput(
    props: Props
):
    RPureComponent<ProcessOutput.Props, ProcessOutput.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    class State: RState


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onFileChanged() {
//        props.dispatcher.dispatchAsync(OutputLookupRequest)
//    }


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


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderContent() {
        renderHeader()

        if (props.processState.columnListing.isNullOrEmpty()) {
            return
        }

        renderOutput()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            child(PageviewIcon::class) {
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

                +"View"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.processState.outputError
        val outputPreview = props.processState.outputInfo?.preview

        styledDiv {
            renderInfo(error)

            if (outputPreview != null) {
                renderPreview(outputPreview)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInfo(error: String?) {
        if (error != null) {
            +"Error: $error"
            return
        }

        val outputInfo = props.processState.outputInfo

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            if (outputInfo == null) {
                +"..."
            }
            else {
                div {
                    +"Absolute path: "

                    styledSpan {
                        css {
                            fontFamily = "monospace"
                        }

                        +outputInfo.absolutePath
                    }
                }

                if (outputInfo.modifiedTime == null) {
//                    +"Does not exist, will create"
                    +"Does not exist, will create"

//                    if (! outputInfo.folderExists) {
//                        +" (along with containing folder)"
//                    }
                }
                else {
                    +"Exists, last modified: ${outputInfo.modifiedTime}"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview(outputPreview: OutputPreview) {
        styledDiv {
            css {
                maxHeight = 30.em
                overflowY = Overflow.auto
            }

            styledTable {
                thead {
                    tr {
                        for (header in outputPreview.header) {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    zIndex = 999
                                }
                                key = header
                                +header
                            }
                        }
                    }
                }

                tbody {
                    for (row in outputPreview.rows.withIndex()) {
                        tr {
                            key = row.index.toString()

                            for (value in row.value.withIndex()) {
                                th {
                                    key = value.index.toString()
                                    +value.value
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}