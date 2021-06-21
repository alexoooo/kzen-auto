package tech.kzen.auto.client.objects.document.report.preview

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.material.VisibilityIcon
import tech.kzen.auto.client.wrap.reactStyle


class PreviewView(
    props: Props
):
    RPureComponent<PreviewView.Props, PreviewView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var afterFilter: Boolean
    }


    interface State: RState {
//        var adding: Boolean
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
        val showRefresh = props.reportState.isTaskRunning()

        val suffix = when (props.afterFilter) {
            true -> "Filtered"
            false -> "All"
        }

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

                if (showRefresh) {
                    child(MaterialButton::class) {
                        attrs {
                            variant = "outlined"
                            size = "small"

                            onClick = {
//                                onSummaryRefresh()
                            }
                        }

                        child(RefreshIcon::class) {
                            attrs {
                                style = reactStyle {
                                    marginRight = 0.25.em
                                }
                            }
                        }
                        +"Refresh"
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview() {
        styledDiv {
            +"[Preview]"
        }
    }
}