package tech.kzen.auto.client.objects.document.pipeline.filter

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.filter.model.PipelineFilterState
import tech.kzen.auto.client.objects.document.pipeline.filter.model.PipelineFilterStore
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconFilter
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary


class PipelineFilterController(
    props: Props
):
    RPureComponent<PipelineFilterController.Props, PipelineFilterController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var filterSpec: FilterSpec
        var runningOrLoading: Boolean
        var filterStore: PipelineFilterStore
        var filterState: PipelineFilterState
        var inputAndCalculatedColumns: HeaderListing?
        var tableSummary: TableSummary?
    }


    interface State: react.State {
//        var adding: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        adding = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSummaryRefresh() {
//        props.dispatcher.dispatchAsync(SummaryLookupRequest)
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
//        val showRefresh = props.reportState.isTaskRunning() && props.reportState.previewFilteredSpec().enabled
        val showRefresh = false

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
                    float = kotlinx.css.Float.right
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
        val filterSpec = props.filterSpec
        styledDiv {
            for ((index, columnName) in filterSpec.columns.keys.withIndex()) {
                styledDiv {
                    key = columnName

                    if (index < filterSpec.columns.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    child(FilterItemController::class) {
                        attrs {
//                            reportState = props.reportState
//                            dispatcher = props.dispatcher
                            this.filterSpec = filterSpec
                            this.columnName = columnName
                            runningOrLoading = props.runningOrLoading
                            inputAndCalculatedColumns = props.inputAndCalculatedColumns
                            tableSummary = props.tableSummary
                            filterStore = props.filterStore
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFilterAdd() {
        child(FilterAddController::class) {
            attrs {
                filterStore = props.filterStore
                filterState = props.filterState
                filterSpec = props.filterSpec
                inputAndCalculatedColumns = props.inputAndCalculatedColumns
            }
        }
    }
}