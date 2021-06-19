package tech.kzen.auto.client.objects.document.report.output

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.report.state.OutputLookupRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.SaveAltIcon
import tech.kzen.auto.client.wrap.material.SettingsIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


class ReportOutputView2(
    props: Props
):
    RPureComponent<ReportOutputView2.Props, ReportOutputView2.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val iconWithPadding = 2.5.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
    }


    interface State: RState {
        var settingsOpen: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        settingsOpen = ! props.reportState.outputSpec().explore.isDefaultWorkPath()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        props.dispatcher.dispatchAsync(OutputLookupRequest)
    }


    private fun onSettingsToggle() {
        setState {
            settingsOpen = ! settingsOpen
        }
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

        if (! props.reportState.columnListing.isNullOrEmpty()) {
            renderOutput()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            css {
                width = 100.pct
                position = Position.relative
            }

            styledSpan {
                css {
                    height = 2.em
                    width = iconWithPadding
                    position = Position.relative
                }

                child(SaveAltIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
//                            width = 2.5.em
                            top = (-0.5).em
                            left = (-3.5).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = iconWithPadding.div(2)
                    fontSize = 2.em
                }

                +"Output"
            }

            styledSpan {
                css {
                    width = 100.pct
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                }

                styledDiv {
                    css {
                        display = Display.table
                        margin(0.px, LinearDimension.auto)
//                        fontWeight = FontWeight.bold
                        fontSize = 1.5.em
                    }

                    val status = props.reportState.outputInfo?.status ?: OutputStatus.Missing
                    if (status == OutputStatus.Missing) {
                        if (props.reportState.columnListing.isNullOrEmpty()) {
                            +"Please select valid input (top of page)"
                        }
                        else {
                            +"Run report using ⏵ (bottom right)"
                        }
                    }
                    else {
                        +"Status: ${status.name}"
                    }
                }
            }

            styledSpan {
                css {
                    float = Float.right
                }

                renderHeaderControls()
            }
        }
    }


    private fun RBuilder.renderHeaderControls() {
        child(MaterialButton::class) {
            attrs {
                title = "Settings"
                variant = "outlined"
                size = "small"

                onClick = {
                    onSettingsToggle()
                }

                style = reactStyle {
                    marginLeft = 1.em

                    if (state.settingsOpen) {
                        backgroundColor = Color.darkGray
                    }
                }
            }

            child(SettingsIcon::class) {
                attrs {
                    style = reactStyle {
                        marginRight = 0.25.em
                    }
                }
            }

            +"Settings"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.reportState.outputError
        val outputInfo = props.reportState.outputInfo
//        val outputPreview = outputInfo?.preview

        styledDiv {
            renderInfo(error, outputInfo)
            renderSettings(outputInfo)

            if (outputInfo != null) {
                renderOutputType()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutputType() {
        styledDiv {
            +"[output]"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInfo(error: String?, outputInfo: OutputInfo?) {
        if (error != null) {
            styledDiv {
                css {
                    marginBottom = 1.em
                    width = 100.pct
                    paddingBottom = 1.em
                    borderBottomWidth = 2.px
                    borderBottomStyle = BorderStyle.solid
                    borderBottomColor = Color.lightGray
                }
                +"Error: $error"
            }
        }
        else if (outputInfo == null) {
            styledDiv {
                +"..."
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSettings(outputInfo: OutputInfo?) {
        if (! state.settingsOpen || outputInfo == null) {
            return
        }

        styledDiv {
            css {
                marginBottom = 1.em
                width = 100.pct
                paddingBottom = 1.em
                borderBottomWidth = 2.px
                borderBottomStyle = BorderStyle.solid
                borderBottomColor = Color.lightGray
            }

            child(AttributePathValueEditor::class) {
                attrs {
                    labelOverride = "Report Work Folder"

                    clientState = props.reportState.clientState
                    objectLocation = props.reportState.mainLocation
                    attributePath = ReportConventions.workDirPath

                    valueType = TypeMetadata.string

                    onChange = {
                        onRefresh()
                    }
                }
            }

            +"Absolute path: ${outputInfo.runDir}"
        }
    }

}