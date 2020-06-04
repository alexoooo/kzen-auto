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
import tech.kzen.auto.common.paradigm.reactive.SummaryProgress
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
        var summaryProgress: SummaryProgress?
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
                    else if (props.summaryProgress != null) {
//                        +"Indexing column values"

                        val remainingFiles = props.summaryProgress!!.remainingFiles

                        if (remainingFiles.isEmpty()) {
                            +"Showing column filters, ready apply filters"
                        }
                        else {
                            +"Showing partial column filters, indexing values"

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
                }
            }
        }

//        styledDiv {
//            css {
//                backgroundColor = Color("rgba(255, 255, 255, 0.5)")
//                padding(0.5.em)
//            }
//
//            if (state.error != null) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Error: ${state.error!!}"
//                }
//            }
//            else if (state.columnListingLoading) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Working: listing columns"
//                }
//            }
//            else if (state.tableSummaryLoading) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Working: indexing column contents"
//                }
//
//                val remainingFiles = state.tableSummaryProgress?.remainingFiles
//                if (remainingFiles != null) {
//                    table {
//                        thead {
//                            tr {
//                                th { +"File" }
//                                th { +"Progress" }
//                            }
//                        }
//                        tbody {
//                            for (e in remainingFiles.entries) {
//                                tr {
//                                    key = e.key
//
//                                    td {
//                                        +e.key
//                                    }
//                                    td {
//                                        +e.value
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            else if (state.writingOutput) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Working: writing output"
//                }
//            }
//            else if (state.wroteOutputPath != null) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Wrote: ${state.wroteOutputPath!!}"
//                }
//            }
//            else {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Showing columns (${state.columnListing?.size})"
//                }
//            }
//        }
    }
}