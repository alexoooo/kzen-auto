package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.em
import kotlinx.css.fontSize
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.MaterialCardContent
import tech.kzen.auto.client.wrap.MaterialPaper
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.lib.common.model.locate.ObjectLocation


class FilterStatus(
    props: Props
):
    RPureComponent<FilterStatus.Props, FilterStatus.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState,

        var error: String?,

        var initialTableSummaryLoading: Boolean,
        var summaryTaskRunning: Boolean,
        var summaryEmpty: Boolean,
        var summaryProgress: TaskProgress?,
        var tableSummary: TableSummary?,

        var filterTaskStateLoading: Boolean,
        var filterTaskRunning: Boolean,
        var filterTaskProgress: TaskProgress?,
        var filterTaskOutput: String?
    ): RProps


    class State(
//        var fileListingLoading: Boolean,
//        var fileListing: List<String>?,
//        var error: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialPaper::class) {
            child(MaterialCardContent::class) {
                styledSpan {
                    css {
                        fontSize = 2.em
                    }
                    +"Status"
                }

                styledDiv {
                    if (props.initialTableSummaryLoading) {
                        +"Looking up column values"
                    }
                    else if (props.filterTaskStateLoading) {
                        +"Looking up filtering state"
                    }
                    else if (props.filterTaskProgress != null) {
//                        +"Indexing column values"

                        val progress = props.filterTaskProgress!!
                        val remainingFiles = progress.remainingFiles

                        if (remainingFiles.isEmpty()) {
                            +"Filtering done"
                        }
                        else {
                            +"Filtering"

                            table {
                                thead {
                                    tr {
                                        th { +"File" }
                                        th { +"Progress" }
                                    }
                                }
                                tbody {
                                    for (e in remainingFiles.entries) {
                                        tr {
                                            key = e.key

                                            td {
                                                +e.key
                                            }
                                            td {
                                                +e.value
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (props.filterTaskOutput != null) {
                        +"Finished filtering, ready to apply filters again"
                    }
                    else if (props.summaryProgress != null) {
//                        +"Indexing column values"

                        val summaryProgress = props.summaryProgress!!
                        val remainingFiles = summaryProgress.remainingFiles

                        if (remainingFiles.isEmpty()) {
                            +"Ready to apply filters"
                        }
                        else if (props.summaryEmpty && ! props.summaryTaskRunning) {
                            +"Column values not indexed yet"
                        }
                        else {
                            +"Indexing column values, showing partial column filters"

                            table {
                                thead {
                                    tr {
                                        th { +"File" }
                                        th { +"Progress" }
                                    }
                                }
                                tbody {
                                    for (e in remainingFiles.entries) {
                                        tr {
                                            key = e.key

                                            td {
                                                +e.key
                                            }
                                            td {
                                                +e.value
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (props.tableSummary != null) {
                        +"Finished indexing column values, ready to apply filters"
//                        +"Ready to apply filters"
                    }
                }
            }
        }
    }
}