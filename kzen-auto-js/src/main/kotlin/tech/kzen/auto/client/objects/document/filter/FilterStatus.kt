package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.padding
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
import tech.kzen.auto.client.wrap.MaterialCircularProgress
import tech.kzen.auto.client.wrap.MaterialPaper
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskState
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
        var initialTableSummaryLoaded: Boolean,
        var summaryTaskRunning: Boolean,
        var summaryEmpty: Boolean,
        var summaryProgress: TaskProgress?,
        var summaryState: TaskState?,
        var tableSummary: TableSummary?,

        var filterTaskStateLoading: Boolean,
        var filterTaskRunning: Boolean,
        var filterTaskProgress: TaskProgress?,
        var filterTaskState: TaskState?,
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

                if (! props.initialTableSummaryLoaded) {
                    styledDiv {
                        css {
                            padding(1.em)
                        }

                        +"Loading..."
                        br {}
                        child(MaterialCircularProgress::class) {}
                    }
                }

                styledDiv {
                    if (props.error != null) {
                        +"Error: ${props.error}"
                    }
                    else if (props.initialTableSummaryLoading) {
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
                        if (props.filterTaskState == TaskState.Cancelled) {
                            +"Filtering stopped, ready to apply filters again"
                        }
                        else {
                            +"Finished filtering, ready to apply filters again"
                        }
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
                            +"Column value indexing is not done, showing partial column filters"

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
                        if (props.summaryState == TaskState.Cancelled) {
                            +"Paused indexing column values, ready to resume or apply filters"
                        }
                        else {
                            +"Finished indexing column values, ready to apply filters"
                        }
                    }
                }
            }
        }
    }
}