package tech.kzen.auto.client.objects.document.report.input

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
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.InputIcon
import tech.kzen.auto.client.wrap.MaterialCircularProgress
import tech.kzen.auto.client.wrap.reactStyle


class ReportInputView(
    props: Props
):
    RPureComponent<ReportInputView.Props, ReportInputView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
    }


    interface State: RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
            }

            styledDiv {
                css {
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct
                }

                styledDiv {
                    css {
                        padding(1.em)
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

        val editDisabled =
            props.reportState.isInitiating() ||
            props.reportState.isTaskRunning()

        div {
            attrs {
                if (editDisabled) {
                    if (props.reportState.isInitiating()) {
                        title = "Disabled while loading"
                    }
//                        else {
//                            "Disabled while task running"
//                        }
                }
            }

            renderBrowseFiles(editDisabled)
            renderSelectedFiles(editDisabled)
        }
    }


    private fun RBuilder.renderHeader() {
        styledDiv {
            child(InputIcon::class) {
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

                +"Input"
            }

            if (props.reportState.inputLoading ||
                    props.reportState.columnListingLoading
            ) {
                styledSpan {
                    css {
                        float = Float.right
                    }

                    child(MaterialCircularProgress::class) {
                        attrs {
                            style = reactStyle {
                                width = 2.em
                                height = 2.em
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderBrowseFiles(editDisabled: Boolean) {
        child(InputBrowser::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                this.editDisabled = editDisabled
            }
        }
    }


    private fun RBuilder.renderSelectedFiles(editDisabled: Boolean) {
        child(InputSelected::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                this.editDisabled = editDisabled
            }
        }
    }
}