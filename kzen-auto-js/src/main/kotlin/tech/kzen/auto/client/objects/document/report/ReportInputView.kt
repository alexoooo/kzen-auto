package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.*
import styled.*
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
import tech.kzen.auto.client.objects.document.report.state.InputsUpdatedRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.InputIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.paradigm.task.model.TaskProgress


class ReportInputView(
    props: Props
):
    RPureComponent<ReportInputView.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


    class State: RState


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAttributeChanged() {
        props.dispatcher.dispatchAsync(InputsUpdatedRequest)
    }


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
//                        padding(0.5.em)
                        padding(1.em)
                    }

                    renderContent()
                }
            }

            child(BottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        val fileListingError = props.reportState.fileListingError
        val columnListingError = props.reportState.columnListingError

        val hasError =
            fileListingError != null ||
            columnListingError != null

        renderHeader()
        renderFilePath(hasError)

        if (fileListingError != null) {
            renderError(fileListingError)
        }
        else {
            renderFileListing()

            if (props.reportState.fileListing?.size ?: 0 > 0) {
                if (columnListingError != null) {
                    renderError(columnListingError)
                }
//                else {
//                    renderColumnListing()
//                }
            }
        }

        val taskProgress = props.reportState.taskProgress
        if (taskProgress != null) {
            renderProgress(taskProgress)
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
        }
    }


    private fun RBuilder.renderFilePath(hasError: Boolean) {
        val editDisabled =
            props.reportState.initiating ||
            props.reportState.isTaskRunning()

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            attrs {
                if (editDisabled) {
                    title =
                        if (props.reportState.initiating) {
                            "Disabled while loading"
                        }
                        else {
                            "Disabled while task running"
                        }
                }
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    clientState = props.reportState.clientState
                    objectLocation = props.reportState.mainLocation
                    attributeName = ReportConventions.inputAttribute
                    labelOverride = "File Path"

                    disabled = editDisabled
                    invalid = hasError

                    onChange = {
                        onAttributeChanged()
                    }
                }
            }
        }
    }


    private fun RBuilder.renderError(message: String) {
        +"Error: $message"
    }


    private fun RBuilder.renderFileListing() {
        val fileListing = props.reportState.fileListing

        styledDiv {
            css {
                maxHeight = 15.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
                position = Position.relative
            }

            styledDiv {
                css {
                    color = Color("rgba(0, 0, 0, 0.54)")
                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
                    fontWeight = FontWeight.w400
                    fontSize = 13.px

                    position = Position.sticky
                    top = 0.px
                    left = 0.px
                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                }
                +"Files"
            }

            when {
                fileListing == null -> {
                    styledDiv {
                        +"Loading..."
                    }
                }

                fileListing.isEmpty() -> {
                    styledDiv {
                        +"Not found"
                    }
                }

                else -> {
                    styledOl {
                        css {
                            marginTop = 0.px
                            marginBottom = 0.px
//                            marginLeft = (-10).px
                        }

                        for (filePath in fileListing) {
                            li {
                                attrs {
                                    key = filePath
                                }

                                styledSpan {
                                    css {
                                        fontFamily = "monospace"
                                    }

                                    +filePath
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderColumnListing() {
        val columnListing = props.reportState.columnListing

        styledDiv {
            css {
                maxHeight = 10.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
                position = Position.relative
            }

            styledDiv {
                css {
                    color = Color("rgba(0, 0, 0, 0.54)")
                    fontFamily = "Roboto, Helvetica, Arial, sans-serif"
                    fontWeight = FontWeight.w400
                    fontSize = 13.px

                    position = Position.sticky
                    top = 0.px
                    left = 0.px
                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                }
                +"Columns"
            }

            when {
                columnListing == null -> {
                    styledDiv {
                        +"Loading..."
                    }
                }

                columnListing.isEmpty() -> {
                    styledDiv {
                        +"Not available"
                    }
                }

                else -> {
                    styledOl {
                        css {
                            marginTop = 0.px
                            marginBottom = 0.px
//                            marginLeft = (-10).px
                        }

                        for (columnName in columnListing) {
                            styledLi {
                                key = columnName

//                                css {
//                                    display = Display.inlineBlock
//                                }

                                +columnName
                            }
                        }
                    }
                }
            }
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
}