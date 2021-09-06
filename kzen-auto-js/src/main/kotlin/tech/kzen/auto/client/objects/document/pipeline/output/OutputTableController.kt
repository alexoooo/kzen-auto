package tech.kzen.auto.client.objects.document.pipeline.output

import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.boxShadowInset
import react.*
import react.dom.attrs
import react.dom.tbody
import react.dom.thead
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputState
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.material.CloudDownloadIcon
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.util.FormatUtils


class OutputTableController(
    props: Props
):
    RPureComponent<OutputTableController.Props, OutputTableController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var spec: OutputSpec
        var inputAndCalculatedColumns: HeaderListing?
        var runningOrLoading: Boolean
        var outputState: PipelineOutputState
        var outputStore: PipelineOutputStore
    }


    interface State: react.State {
        var settingsOpen: Boolean
//        var savingOpen: Boolean
//        var savingLoading: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        settingsOpen = ! props.spec.explore.isDefaultWorkPath()
//        savingOpen = false
//        savingLoading = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onPreviewRefresh() {
        props.outputStore.lookupOutputWithFallbackAsync()
    }


    private fun abbreviate(value: String): String {
        if (value.length < 50) {
            return value
        }
        return value.substring(0, 47) + "..."
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSettingsToggle() {
        setState {
            settingsOpen = ! settingsOpen
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                float = Float.right
            }
            renderHeaderControls()
        }

        if (! props.inputAndCalculatedColumns?.values.isNullOrEmpty()) {
            renderOutput()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeaderControls() {
        val showRefresh = props.runningOrLoading
        val showDownload = (props.outputState.outputInfo?.status ?: OutputStatus.Missing) != OutputStatus.Missing

        if (showRefresh) {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = {
                        onPreviewRefresh()
                    }

                    style = reactStyle {
                        borderWidth = 2.px
                        marginLeft = 0.5.em
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
        else if (showDownload) {
            val linkAddress = ClientContext.restClient.linkDetachedDownload(
                props.outputStore.mainLocation())

            styledA {
                css {
                    textDecoration = TextDecoration.none
                    marginLeft = 0.5.em
                }

                attrs {
                    href = linkAddress
                }

                child(MaterialButton::class) {
                    attrs {
                        variant = "outlined"
                        size = "small"
                        style = reactStyle {
                            borderWidth = 2.px
                        }
                    }

                    child(CloudDownloadIcon::class) {
                        attrs {
                            style = reactStyle {
                                marginRight = 0.25.em
                            }
                        }
                    }

                    +"Download"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutput() {
        val error = props.outputState.outputInfoError
        val outputInfo = props.outputState.outputInfo
        val outputPreview = outputInfo?.table?.preview

        styledDiv {
//            +"[${props.inputAndCalculatedColumns?.values}]"

            renderInfo(error, outputInfo)
//            renderSave(outputInfo)

            if (outputPreview != null) {
                renderPreview(outputInfo, outputPreview)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInfo(error: String?, outputInfo: OutputInfo?) {
        if (error != null) {
            styledDiv {
                css {
                    marginBottom = 1.em
                    width = 100.pct
                    paddingBottom = 1.em
                    borderBottomWidth = 2.px
                    borderBottomStyle = BorderStyle.solid
                    borderBottomColor = Color.lightGray
                }
                +"Error: $error"
            }
        }
        else {
            styledDiv {
                if (outputInfo == null) {
                    +"..."
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPreview(outputInfo: OutputInfo, outputPreview: OutputPreview) {
        renderPreviewHeader(outputInfo)
        renderPreviewTable(outputPreview)
    }


    private fun RBuilder.renderPreviewHeader(outputInfo: OutputInfo) {
        if (outputInfo.status == OutputStatus.Missing) {
            return
        }

        styledDiv {
            css {
                width = 100.pct
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                }

                child(TextAttributeEditor::class) {
                    attrs {
                        objectLocation = props.outputStore.mainLocation()
                        attributePath = ReportConventions.previewStartPath

                        value = props.spec.explore.previewStart
                        type = TextAttributeEditor.Type.Number

                        labelOverride = "Preview Start Row"

                        onChange = {
                            onPreviewRefresh()
                        }
                    }

                    key = "start-row"
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    marginLeft = 1.em
                }

                child(TextAttributeEditor::class) {
                    attrs {
                        objectLocation = props.outputStore.mainLocation()
                        attributePath = ReportConventions.previewCountPath

                        value = props.spec.explore.previewCount
                        type = TextAttributeEditor.Type.Number

                        labelOverride = "Preview Row Count"

                        onChange = {
                            onPreviewRefresh()
                        }
                    }

                    key = "row-count"
                }
            }

            styledDiv {
                css {
                    float = Float.right
                }

                styledDiv {
                    css {
                        display = Display.inlineBlock
                        marginLeft = 1.em
                    }

//                    +"Total rows: ${FormatUtils.decimalSeparator(props.reportState.outputCount())}"
                    +"[total rows]"
                }
            }
        }
    }


    private fun RBuilder.renderPreviewTable(outputPreview: OutputPreview) {
        styledDiv {
            css {
                maxHeight = 25.em
                overflowY = Overflow.auto
                marginTop = 1.em
                borderWidth = 2.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                    minWidth = 100.pct
                }

                thead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                left = 0.px
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 1000
                                width = 2.em
                                height = 2.em
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, (-2).px, (-2).px, 0.px, 0.px)
//                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
//                                boxShadow(Color.lightGray, 2.px, 2.px, 2.px, 0.px)
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                            }
                            +"Row Number"
                        }

                        for (header in outputPreview.header.values) {
                            styledTh {
                                css {
                                    position = Position.sticky
                                    top = 0.px
//                                    backgroundColor = Color("rgba(255, 255, 255, 0.9)")
                                    backgroundColor = Color.white
                                    zIndex = 999
//                                    boxShadow(Color.lightGray, 0.px, 2.px, 2.px, 0.px)
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    textAlign = TextAlign.left
                                    boxShadowInset(Color.lightGray, 0.px, (-2).px, 0.px, 0.px)
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

                            css {
//                                backgroundColor = Color.white
                                hover {
                                    backgroundColor = Color.lightGrey
                                }
                            }

                            styledTd {
                                css {
                                    position = Position.sticky
                                    left = 0.px
                                    backgroundColor = Color.white
                                    zIndex = 999

                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    boxShadowInset(Color.lightGray, (-2).px, 0.px, 0.px, 0.px)

                                    if (row.index != 0) {
                                        borderTopWidth = 1.px
                                        borderTopStyle = BorderStyle.solid
                                        borderTopColor = Color.lightGray
                                    }
                                }
                                val rowNumber = row.index + outputPreview.startRow.coerceAtLeast(0)
                                val rowFormat = FormatUtils.decimalSeparator(rowNumber + 1)
                                +rowFormat
                            }

                            for (value in row.value.withIndex()) {
                                styledTd {
                                    css {
                                        paddingLeft = 0.5.em
                                        paddingRight = 0.5.em

                                        if (row.index != 0) {
                                            borderTopWidth = 1.px
                                            borderTopStyle = BorderStyle.solid
                                            borderTopColor = Color.lightGray
                                        }
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