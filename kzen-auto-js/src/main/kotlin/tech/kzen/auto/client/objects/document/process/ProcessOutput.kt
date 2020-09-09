package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.div
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.process.state.ListInputsRequest
import tech.kzen.auto.client.objects.document.process.state.OutputLookupRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.SaveAltIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.filter.FilterConventions


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
    private fun onFileChanged() {
        props.dispatcher.dispatchAsync(OutputLookupRequest)
    }


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
            renderFile()
            renderInfo()
        }
    }


    private fun RBuilder.renderFile() {
        styledDiv {
            css {
                marginTop = 0.5.em
            }

            attrs {
                if (props.processState.filterTaskRunning) {
                    title = "Disabled filter running"
                }
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    clientState = props.processState.clientState
                    objectLocation = props.processState.mainLocation
                    attributeName = FilterConventions.outputAttribute
                    labelOverride = "File"
                    disabled = props.processState.filterTaskRunning
                    onChange = {
                        onFileChanged()
                    }
                }
            }
        }
    }


    private fun RBuilder.renderInfo() {
        val error = props.processState.outputError
        if (error != null) {
            +"Lookup error: $error"
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
                    +"Does not exist, will create"

                    if (! outputInfo.folderExists) {
                        +" (along with containing folder)"
                    }
                }
                else {
                    +"Exists, last modified: ${outputInfo.modifiedTime}"
                }
            }
        }
    }
}