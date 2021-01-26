package tech.kzen.auto.client.objects.document.report.input

import kotlinx.css.*
import react.*
import react.dom.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import styled.styledTh
import tech.kzen.auto.client.objects.document.report.state.InputsUpdatedRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.ExpandLessIcon
import tech.kzen.auto.client.wrap.ExpandMoreIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.common.paradigm.task.model.TaskProgress


class InputSelected(
    props: Props
):
    RPureComponent<InputSelected.Props, InputSelected.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: RState {
        var selectedOpen: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        selectedOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAttributeChanged() {
        props.dispatcher.dispatchAsync(InputsUpdatedRequest)
    }


    private fun onToggleSelected() {
        setState {
            selectedOpen = ! selectedOpen
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val fileListing = props.reportState.inputSelected
        val forceOpen = fileListing?.isEmpty() ?: false

        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
                marginTop = 1.em
            }

            styledDiv {
                css {
                    width = 100.pct
                }

                styledSpan {
                    css {
                        fontSize = 1.5.em
                    }
                    +"Selected"
                }

                if (! forceOpen) {
                    styledSpan {
                        css {
                            float = kotlinx.css.Float.right
                        }

                        child(MaterialIconButton::class) {
                            attrs {
                                onClick = {
                                    onToggleSelected()
                                }
                            }

                            if (state.selectedOpen) {
                                child(ExpandLessIcon::class) {}
                            } else {
                                child(ExpandMoreIcon::class) {}
                            }
                        }
                    }
                }
            }

            styledDiv {
                when {
                    fileListing == null -> {
                        +"Loading..."
                    }

                    fileListing.isEmpty() -> {
                        +"None (please select in Browser above)"
                    }

                    state.selectedOpen -> {
                        +fileListing.map { it.toString() }.toString()
                    }

                    else -> {
                        +fileListing.map { it.name }.toString()
                    }
                }
            }
        }

        val taskProgress = props.reportState.taskProgress
        if (taskProgress != null) {
            renderProgress(taskProgress)
        }
    }


    private fun RBuilder.renderProgress(taskProgress: TaskProgress) {
        if (! props.reportState.isTaskRunning()) {
            return
        }

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            styledDiv {
                css {
                    color = Color("rgba(0, 0, 0, 0.54)")
                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
                    fontWeight = FontWeight.w400
                    fontSize = 13.px
                }

                when {
                    props.reportState.taskRunning -> {
                        +"Running"
                    }

//                    props.reportState.indexTaskRunning -> {
//                        +"Indexing"
//                    }
                }
            }

            styledDiv {
                css {
                    maxHeight = 20.em
                    overflowY = Overflow.auto
                }

                table {
                    thead {
                        tr {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    zIndex = 999
                                }
                                +"File"
                            }

                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    zIndex = 999
                                }
                                +"Progress"
                            }
                        }
                    }
                    tbody {
                        for (e in taskProgress.remainingFiles.entries) {
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



//    private fun RBuilder.renderColumnListing() {
//        val columnListing = props.reportState.columnListing
//
//        styledDiv {
//            css {
//                maxHeight = 10.em
//                overflowY = Overflow.auto
//                marginTop = 0.5.em
//                position = Position.relative
//            }
//
//            styledDiv {
//                css {
//                    color = Color("rgba(0, 0, 0, 0.54)")
//                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
//                    fontWeight = FontWeight.w400
//                    fontSize = 13.px
//
//                    position = Position.sticky
//                    top = 0.px
//                    left = 0.px
//                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
//                }
//                +"Columns"
//            }
//
//            when {
//                columnListing == null -> {
//                    styledDiv {
//                        +"Loading..."
//                    }
//                }
//
//                columnListing.isEmpty() -> {
//                    styledDiv {
//                        +"Not available"
//                    }
//                }
//
//                else -> {
//                    styledOl {
//                        css {
//                            marginTop = 0.px
//                            marginBottom = 0.px
////                            marginLeft = (-10).px
//                        }
//
//                        for (columnName in columnListing) {
//                            styledLi {
//                                key = columnName
//
////                                css {
////                                    display = Display.inlineBlock
////                                }
//
//                                +columnName
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

}