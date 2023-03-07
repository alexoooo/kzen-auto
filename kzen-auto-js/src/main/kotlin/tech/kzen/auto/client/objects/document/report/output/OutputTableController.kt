package tech.kzen.auto.client.objects.document.report.output
//
//import kotlinx.css.*
//import kotlinx.css.properties.TextDecoration
//import kotlinx.css.properties.boxShadowInset
//import react.Props
//import react.RBuilder
//import react.RPureComponent
//import react.State
//import react.dom.attrs
//import react.dom.tbody
//import react.dom.thead
//import react.dom.tr
//import styled.*
//import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
//import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputState
//import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
//import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.wrap.material.CloudDownloadIcon
//import tech.kzen.auto.client.wrap.material.MaterialButton
//import tech.kzen.auto.client.wrap.material.RefreshIcon
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.common.objects.document.report.ReportConventions
//import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
//import tech.kzen.auto.common.objects.document.report.output.OutputInfo
//import tech.kzen.auto.common.objects.document.report.output.OutputPreview
//import tech.kzen.auto.common.objects.document.report.output.OutputStatus
//import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
//import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
//import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
//import tech.kzen.auto.common.util.FormatUtils
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface OutputTableControllerProps: Props {
//    var spec: OutputSpec
//    var analysisSpec: AnalysisSpec
//    var filteredColumns: HeaderListing?
//    var runningOrLoading: Boolean
//    var progress: ReportRunProgress?
//    var outputState: ReportOutputState
//    var outputStore: ReportOutputStore
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class OutputTableController(
//    props: OutputTableControllerProps
//):
//    RPureComponent<OutputTableControllerProps, State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onPreviewRefresh() {
//        props.outputStore.lookupOutputWithFallbackAsync()
//    }
//
//
//    private fun abbreviate(value: String): String {
//        if (value.length < 50) {
//            return value
//        }
//        return value.substring(0, 47) + "..."
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        styledDiv {
//            css {
//                float = Float.right
//            }
//            renderHeaderControls()
//        }
//
//        if (! props.filteredColumns?.values.isNullOrEmpty()) {
//            renderOutput()
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderHeaderControls() {
//        val showRefresh = props.runningOrLoading
//        val showDownload = (props.outputState.outputInfo?.status ?: OutputStatus.Missing) != OutputStatus.Missing
//
//        if (showRefresh) {
//            child(MaterialButton::class) {
//                attrs {
//                    variant = "outlined"
//                    size = "small"
//
//                    onClick = {
//                        onPreviewRefresh()
//                    }
//
//                    style = reactStyle {
//                        borderWidth = 2.px
//                        marginLeft = 0.5.em
//                        color = Color.black
//                        borderColor = Color("#c4c4c4")
//                    }
//                }
//
//                child(RefreshIcon::class) {
//                    attrs {
//                        style = reactStyle {
//                            marginRight = 0.25.em
//                        }
//                    }
//                }
//                +"Refresh"
//            }
//        }
//        else if (showDownload) {
//            val linkAddress = ClientContext.restClient.linkDetachedDownload(
//                props.outputStore.mainLocation())
//
//            styledA {
//                css {
//                    textDecoration = TextDecoration.none
//                    marginLeft = 0.5.em
//                }
//
//                attrs {
//                    href = linkAddress
//                }
//
//                child(MaterialButton::class) {
//                    attrs {
//                        variant = "outlined"
//                        size = "small"
//                        style = reactStyle {
//                            borderWidth = 2.px
//                            color = Color.black
//                            borderColor = Color("#c4c4c4")
//                        }
//                    }
//
//                    child(CloudDownloadIcon::class) {
//                        attrs {
//                            style = reactStyle {
//                                marginRight = 0.25.em
//                            }
//                        }
//                    }
//
//                    +"Download"
//                }
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderOutput() {
//        val outputInfo = props.outputState.outputInfo
//        val outputPreview = outputInfo?.table?.preview
//
//        styledDiv {
//            if (outputPreview != null && outputPreview.rows.isNotEmpty()) {
//                renderPreviewHeader(outputInfo)
//                renderPreviewTable(outputPreview)
//            }
//            else if (outputInfo != null) {
//                renderPreviewHeader(outputInfo)
//                renderPreviewTablePlaceholder()
//            }
//            else {
//                renderPreviewTablePlaceholder()
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderPreviewHeader(outputInfo: OutputInfo) {
//        if (outputInfo.status == OutputStatus.Missing) {
//            return
//        }
//
//        val outputCount =
//            if (props.runningOrLoading) {
//                props.progress?.snapshot?.values?.get(ReportConventions.outputTracePath)?.get()?.let { it as Long }
//            }
//            else {
//                props.outputState.outputInfo?.table?.rowCount
//            }
//
//        styledDiv {
//            css {
//                width = 100.pct
//            }
//
//            styledDiv {
//                css {
//                    display = Display.inlineBlock
//                }
//
//                child(TextAttributeEditor::class) {
//                    attrs {
//                        objectLocation = props.outputStore.mainLocation()
//                        attributePath = OutputExploreSpec.previewStartPath
//
//                        value = props.spec.explore.previewStart
//                        type = TextAttributeEditor.Type.Number
//
//                        labelOverride = "Preview Start Row"
//
//                        onChange = {
//                            onPreviewRefresh()
//                        }
//                    }
//
//                    key = "start-row"
//                }
//            }
//
//            styledDiv {
//                css {
//                    display = Display.inlineBlock
//                    marginLeft = 1.em
//                }
//
//                child(TextAttributeEditor::class) {
//                    attrs {
//                        objectLocation = props.outputStore.mainLocation()
//                        attributePath = OutputExploreSpec.previewCountPath
//
//                        value = props.spec.explore.previewCount
//                        type = TextAttributeEditor.Type.Number
//
//                        labelOverride = "Preview Row Count"
//
//                        onChange = {
//                            onPreviewRefresh()
//                        }
//                    }
//
//                    key = "row-count"
//                }
//            }
//
//            if (outputCount != null) {
//                styledDiv {
//                    css {
//                        float = Float.right
//                    }
//
//                    styledDiv {
//                        css {
//                            display = Display.inlineBlock
//                            marginLeft = 1.em
//                        }
//
//                        +"Total rows: ${FormatUtils.decimalSeparator(outputCount)}"
//                    }
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderPreviewTable(outputPreview: OutputPreview) {
//        styledDiv {
//            css {
//                maxHeight = 25.em
//                overflowY = Overflow.auto
//                marginTop = 1.em
//                borderWidth = 2.px
//                borderStyle = BorderStyle.solid
//                borderColor = Color.lightGray
//            }
//
//            styledTable {
//                css {
//                    borderCollapse = BorderCollapse.collapse
//                    width = 100.pct
//                }
//
//                thead {
//                    tr {
//                        styledTh {
//                            css {
//                                position = Position.sticky
//                                left = 0.px
//                                top = 0.px
//                                backgroundColor = Color.white
//                                zIndex = 1000
//                                width = 2.em
//                                height = 2.em
//                                textAlign = TextAlign.left
//                                boxShadowInset(Color.lightGray, (-2).px, (-2).px, 0.px, 0.px)
//                                paddingLeft = 0.5.em
//                                paddingRight = 0.5.em
//                            }
//                            +"Row Number"
//                        }
//
//                        for (header in outputPreview.header.values) {
//                            styledTh {
//                                css {
//                                    position = Position.sticky
//                                    top = 0.px
//                                    backgroundColor = Color.white
//                                    zIndex = 999
//                                    paddingLeft = 0.5.em
//                                    paddingRight = 0.5.em
//                                    textAlign = TextAlign.left
//                                    boxShadowInset(Color.lightGray, 0.px, (-2).px, 0.px, 0.px)
//                                }
//                                key = header
//                                +header
//                            }
//                        }
//                    }
//                }
//
//                tbody {
//                    for (row in outputPreview.rows.withIndex()) {
//                        styledTr {
//                            key = row.index.toString()
//
//                            css {
//                                hover {
//                                    backgroundColor = Color.lightGrey
//                                }
//                            }
//
//                            styledTd {
//                                css {
//                                    position = Position.sticky
//                                    left = 0.px
//                                    backgroundColor = Color.white
//                                    zIndex = 999
//
//                                    paddingLeft = 0.5.em
//                                    paddingRight = 0.5.em
//                                    boxShadowInset(Color.lightGray, (-2).px, 0.px, 0.px, 0.px)
//
//                                    if (row.index != 0) {
//                                        borderTopWidth = 1.px
//                                        borderTopStyle = BorderStyle.solid
//                                        borderTopColor = Color.lightGray
//                                    }
//                                }
//                                val rowNumber = row.index + outputPreview.startRow.coerceAtLeast(0)
//                                val rowFormat = FormatUtils.decimalSeparator(rowNumber + 1)
//                                +rowFormat
//                            }
//
//                            for (value in row.value.withIndex()) {
//                                styledTd {
//                                    css {
//                                        paddingLeft = 0.5.em
//                                        paddingRight = 0.5.em
//
//                                        if (row.index != 0) {
//                                            borderTopWidth = 1.px
//                                            borderTopStyle = BorderStyle.solid
//                                            borderTopColor = Color.lightGray
//                                        }
//                                    }
//
//                                    key = value.index.toString()
//                                    +abbreviate(value.value)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderPreviewTablePlaceholder() {
//        val filteredColumns = props.filteredColumns
//            ?: return
//
//        val headerListing = OutputPreview.emptyHeaderListing(
//            filteredColumns, props.analysisSpec)
//
//        styledDiv {
//            css {
//                marginTop = 1.em
//                overflowX = Overflow.auto
//                borderWidth = 2.px
//                borderStyle = BorderStyle.solid
//                borderColor = Color.lightGray
//            }
//
//            styledTable {
//                css {
//                    borderCollapse = BorderCollapse.collapse
//                    width = 100.pct
//                }
//
//                thead {
//                    tr {
//                        styledTh {
//                            css {
//                                width = 2.em
//                                height = 2.em
//                                textAlign = TextAlign.left
//                                paddingLeft = 0.5.em
//                                paddingRight = 0.5.em
//                            }
//                            +"Row Number"
//                        }
//
//                        for (header in headerListing.values) {
//                            styledTh {
//                                css {
//                                    paddingLeft = 0.5.em
//                                    paddingRight = 0.5.em
//                                    textAlign = TextAlign.left
//                                }
//                                key = header
//                                +header
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//}