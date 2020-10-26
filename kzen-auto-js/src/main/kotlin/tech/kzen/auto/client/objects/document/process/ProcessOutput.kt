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
import tech.kzen.auto.client.objects.document.process.state.OutputLookupRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.PageviewIcon
import tech.kzen.auto.client.wrap.reactStyle


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


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderContent() {
        renderHeader()

        if (props.processState.columnListing == null) {
            // TODO: is this good usability?
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

//                +"Output"
                +"View"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.processState.outputError

        styledDiv {
//            renderFile(error)
            renderInfo(error)
            renderPreview(error)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderFile(error: String?) {
        val editDisabled =
            props.processState.initiating ||
            props.processState.filterTaskRunning

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            attrs {
                if (editDisabled) {
                    title =
                        if (props.processState.initiating) {
                            "Disabled while loading"
                        }
                        else {
                            "Disabled while filter running"
                        }
                }
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    clientState = props.processState.clientState
                    objectLocation = props.processState.mainLocation
//                    attributeName = ProcessConventions.outputAttribute
                    labelOverride = "File"

                    disabled = editDisabled
                    invalid = error != null

                    onChange = {
                        onFileChanged()
                    }
                }
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
    private fun RBuilder.renderPreview(error: String?) {
        if (error != null) {
            return
        }

        styledDiv {
            +"[Preview]"
        }
    }
}