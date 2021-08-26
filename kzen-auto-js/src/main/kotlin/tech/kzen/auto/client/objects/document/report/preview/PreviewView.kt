package tech.kzen.auto.client.objects.document.report.preview

import kotlinx.css.*
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.objects.document.report.state.PreviewChangeEnabledRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.material.MaterialSwitch
import tech.kzen.auto.client.wrap.material.VisibilityIcon
import tech.kzen.auto.client.wrap.reactStyle


class PreviewView(
    props: Props
):
    RPureComponent<PreviewView.Props, PreviewView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var afterFilter: Boolean
    }


    interface State: react.State {
//        var adding: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onEnabledChange(enabled: Boolean) {
        props.dispatcher.dispatchAsync(
            PreviewChangeEnabledRequest(props.afterFilter, enabled))
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

            child(ReportBottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderPreview()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        val previewSpec = props.reportState.previewSpec(props.afterFilter)
        val previewEnabled = previewSpec.enabled

        val running = props.reportState.isTaskRunning()
        val disabled = props.reportState.isInitiating() || running
//        val showRefresh = running && previewEnabled

//        val suffix = when (props.afterFilter) {
//            false -> "All"
//            true -> "Filtered"
//        }

        styledDiv {
            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                child(VisibilityIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-17).px
                            left = (-3).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

//                +"Preview $suffix"
                +"Preview"
            }

            styledSpan {
                css {
                    float = Float.right
                }

//                if (showRefresh) {
//                    styledSpan {
//                        child(MaterialButton::class) {
//                            attrs {
//                                variant = "outlined"
//                                size = "small"
//
//                                onClick = {
////                                onSummaryRefresh()
//                                }
//                            }
//
//                            child(RefreshIcon::class) {
//                                attrs {
//                                    style = reactStyle {
//                                        marginRight = 0.25.em
//                                    }
//                                }
//                            }
//                            +"Refresh"
//                        }
//                    }
//                }

                renderEnable(previewEnabled, disabled)
            }
        }
    }


    private fun RBuilder.renderEnable(previewEnabled: Boolean, disabled: Boolean) {
        val inputId = "material-react-switch-id"
        styledSpan {
            child(MaterialInputLabel::class) {
                attrs {
                    htmlFor = inputId

                    style = reactStyle {
                        fontSize = 0.8.em
                    }
                }

                if (previewEnabled) {
                    +"Enabled"
                }
                else {
                    +"Disabled"
                }
            }

            child(MaterialSwitch::class) {
                attrs {
                    id = inputId
                    checked = previewEnabled
                    this.disabled = disabled
                    onChange = {
                        val target = it.target as HTMLInputElement
                        onEnabledChange(target.checked)
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview() {
        styledDiv {
            //child(InfoIcon::class) {}

            styledSpan {
                css {
                    fontSize = 1.25.em
                    fontStyle = FontStyle.italic
                }
                +"Must be enabled for suggestions to appear in Filter"
            }
        }
    }
}