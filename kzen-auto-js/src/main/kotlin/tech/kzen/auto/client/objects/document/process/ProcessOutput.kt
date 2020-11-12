package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import kotlinx.css.properties.boxShadow
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.tbody
import react.dom.thead
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.process.state.OutputLookupRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.PageviewIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.process.OutputInfo
import tech.kzen.auto.common.objects.document.process.OutputPreview
import tech.kzen.auto.common.objects.document.process.ProcessConventions
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


class ProcessOutput(
    props: Props
):
    RPureComponent<ProcessOutput.Props, ProcessOutput.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    class State: RState


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onFileChanged() {
//        props.dispatcher.dispatchAsync(OutputLookupRequest)
//    }

    private fun onPreviewRefresh() {
        props.dispatcher.dispatchAsync(OutputLookupRequest)
    }


    private fun abbreviate(value: String): String {
        if (value.length < 50) {
            return value
        }

//        console.log("^^^^ abbreviating: $value")
        return value.substring(0, 47) + "..."
    }


    private fun formatRow(rowNumber: Long): String {
        return rowNumber
            .toString()
            .replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
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

            child(TopIngress::class) {
                attrs {
                    ingressColor = Color.white
                    parentWidth = 100.pct
                }
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
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderContent() {
        renderHeader()

        if (props.processState.columnListing.isNullOrEmpty()) {
            return
        }

        renderOutput()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            child(PageviewIcon::class) {
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

                +"View"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.processState.outputError
        val outputInfo = props.processState.outputInfo
        val outputPreview = outputInfo?.preview

        styledDiv {
            renderInfo(error)

            if (outputPreview != null) {
                renderPreview(outputInfo, outputPreview)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInfo(error: String?) {
        if (error != null) {
            +"Error: $error"
            return
        }

        val outputInfo = props.processState.outputInfo

        styledDiv {
            css {
                marginTop = 0.5.em
            }

            if (outputInfo == null) {
                +"..."
            }
            else {
//                div {
//                    +"Absolute path: "
//
//                    styledSpan {
//                        css {
//                            fontFamily = "monospace"
//                        }
//
//                        +outputInfo.absolutePath
//                    }
//                }

                if (outputInfo.modifiedTime == null) {
                    +"Does not exist, will create"
                }
//                else {
//                    +"Exists, last modified: ${outputInfo.modifiedTime}"
//                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview(outputInfo: OutputInfo, outputPreview: OutputPreview) {
        renderPreviewHeader(outputInfo)
        renderPreviewTable(outputPreview)
    }


    private fun RBuilder.renderPreviewHeader(outputInfo: OutputInfo) {
        styledDiv {
            styledDiv {
                css {
                    display =Display.inlineBlock
                }

                child(AttributePathValueEditor::class) {
                    attrs {
                        labelOverride = "Start row"
//                disabled = props.disabled
//                invalid = props.invalid

                        clientState = props.processState.clientState
                        objectLocation = props.processState.mainLocation
                        attributePath = ProcessConventions.previewStartPath

                        valueType = TypeMetadata.long

                        onChange = {
                            onPreviewRefresh()
                        }
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    marginLeft = 1.em
                }

                child(AttributePathValueEditor::class) {
                    attrs {
                        labelOverride = "Row count"

                        clientState = props.processState.clientState
                        objectLocation = props.processState.mainLocation
                        attributePath = ProcessConventions.previewCountPath

                        valueType = TypeMetadata.int

                        onChange = {
                            onPreviewRefresh()
                        }
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    marginLeft = 1.em
                }

                +"Total rows: ${formatRow(outputInfo.rowCount)}"
            }
        }
    }


    private fun RBuilder.renderPreviewTable(outputPreview: OutputPreview) {
        styledDiv {
            css {
                maxHeight = 30.em
                overflowY = Overflow.auto
                marginTop = 0.5.em
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                }

                thead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
//                                backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                backgroundColor = Color.white
                                zIndex = 999
                                boxShadow(Color.lightGray, 0.px, 2.px, 2.px, 0.px)
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                            }
                            +"Row Number"
                        }

                        for (header in outputPreview.header) {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
//                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    backgroundColor = Color.white
                                    zIndex = 999
                                    boxShadow(Color.lightGray, 0.px, 2.px, 2.px, 0.px)
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                key = header
                                +header
                            }
                        }
                    }
                }

                tbody {
                    for (row in outputPreview.rows.withIndex()) {
                        styledTr {
                            key = row.index.toString()

                            styledTd {
                                css {
                                    borderTopStyle = BorderStyle.solid
                                    borderTopColor = Color.lightGray
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                val rowNumber = row.index + outputPreview.startRow
                                val rowFormat = formatRow(rowNumber + 1)
                                +rowFormat
                            }

                            for (value in row.value.withIndex()) {
                                styledTd {
                                    css {
                                        borderTopStyle = BorderStyle.solid
                                        borderTopColor = Color.lightGray
                                        paddingLeft = 0.5.em
                                        paddingRight = 0.5.em
                                    }

                                    key = value.index.toString()
                                    +abbreviate(value.value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}