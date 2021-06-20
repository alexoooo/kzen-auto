package tech.kzen.auto.client.objects.document.report.filter

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
import tech.kzen.auto.client.objects.document.report.state.SummaryLookupRequest
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconFilter
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.reactStyle


class ReportFilterList(
    props: Props
):
    RPureComponent<ReportFilterList.Props, ReportFilterList.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


    class State(
//        var adding: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        adding = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSummaryRefresh() {
        props.dispatcher.dispatchAsync(SummaryLookupRequest)
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
        renderFilters()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        val showRefresh = props.reportState.isTaskRunning()

        styledDiv {
            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                styledSpan {
                    css {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-16.5).px
                        left = (-3.5).px
                    }
                    iconify(vaadinIconFilter)
                }

//                child(FilterListIcon::class) {
//                    attrs {
//                        style = reactStyle {
//                            position = Position.absolute
//                            fontSize = 2.5.em
//                            top = (-16.5).px
//                            left = (-3.5).px
//                        }
//                    }
//                }
            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Filter"
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
                                onSummaryRefresh()
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
    private fun RBuilder.renderFilters() {
        styledDiv {
            renderFilterList()
            renderFilterAdd()
        }
    }


    private fun RBuilder.renderFilterList() {
        val filterSpec = props.reportState.filterSpec()
        styledDiv {
            for ((index, columnName) in filterSpec.columns.keys.withIndex()) {
                styledDiv {
                    key = columnName

                    if (index < filterSpec.columns.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    child(ReportFilterItem::class) {
                        attrs {
                            reportState = props.reportState
                            dispatcher = props.dispatcher
                            this.filterSpec = filterSpec
                            this.columnName = columnName
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFilterAdd() {
        child(ReportFilterAdd::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
            }
        }
    }
}