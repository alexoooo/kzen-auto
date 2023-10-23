package tech.kzen.auto.client.objects.document.report.filter

import emotion.react.css
import js.core.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterState
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.objects.document.report.widget.ReportBottomEgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconFilter
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ReportFilterControllerProps: react.Props {
    var filterSpec: FilterSpec
    var runningOrLoading: Boolean
    var filterStore: ReportFilterStore
    var filterState: ReportFilterState
    var inputAndCalculatedColumns: HeaderListing?
    var tableSummary: TableSummary?
}


//---------------------------------------------------------------------------------------------------------------------
class ReportFilterController(
    props: ReportFilterControllerProps
):
    RPureComponent<ReportFilterControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onSummaryRefresh() {
//        props.dispatcher.dispatchAsync(SummaryLookupRequest)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                height = 100.pct
                marginTop = 5.px
            }

            div {
                css {
                    borderRadius = 3.px
                    backgroundColor = NamedColor.white
                    width = 100.pct
                }

                div {
                    css {
                        padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                    }

                    renderContent()
                }
            }

            ReportBottomEgress::class.react {
                this.egressColor = NamedColor.white
                parentWidth = 100.pct
            }
        }
    }


    private fun ChildrenBuilder.renderContent() {
        renderHeader()
        renderFilters()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeader() {
//        val showRefresh = props.reportState.isTaskRunning() && props.reportState.previewFilteredSpec().enabled
        val showRefresh = false

        div {
            span {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                span {
                    css {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-16.5).px
                        left = (-3.5).px
                    }
                    iconify(vaadinIconFilter)
                }
            }

            span {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Filter"
            }

            span {
                css {
                    float = Float.right
                }

                if (showRefresh) {
                    Button {
                        variant = ButtonVariant.outlined
                        size = Size.small

                        onClick = {
                            onSummaryRefresh()
                        }

                        RefreshIcon::class.react {
                            style = jso {
                                marginRight = 0.25.em
                            }
                        }
                        +"Refresh"
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderFilters() {
        div {
            renderFilterList()
            renderFilterAdd()
        }
    }


    private fun ChildrenBuilder.renderFilterList() {
        val filterSpec = props.filterSpec
        div {
            for ((index, columnName) in filterSpec.columns.keys.withIndex()) {
                div {
                    key = columnName

                    if (index < filterSpec.columns.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    FilterItemController::class.react {
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


    private fun ChildrenBuilder.renderFilterAdd() {
        FilterAddController::class.react {
            filterStore = props.filterStore
            filterState = props.filterState
            filterSpec = props.filterSpec
            inputAndCalculatedColumns = props.inputAndCalculatedColumns
        }
    }
}