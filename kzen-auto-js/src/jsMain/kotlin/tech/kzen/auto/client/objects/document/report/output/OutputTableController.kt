package tech.kzen.auto.client.objects.document.report.output

import emotion.react.css
import js.core.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputState
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.CloudDownloadIcon
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.util.FormatUtils
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface OutputTableControllerProps: Props {
    var spec: OutputSpec
    var analysisSpec: AnalysisSpec
    var filteredColumns: HeaderListing?
    var runningOrLoading: Boolean
    var progress: ReportRunProgress?
    var outputState: ReportOutputState
    var outputStore: ReportOutputStore
}


//---------------------------------------------------------------------------------------------------------------------
class OutputTableController(
    props: OutputTableControllerProps
):
    RPureComponent<OutputTableControllerProps, State>(props)
{
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
    override fun ChildrenBuilder.render() {
        div {
            css {
                float = Float.right
            }
            renderHeaderControls()
        }

        if (! props.filteredColumns?.values.isNullOrEmpty()) {
            renderOutput()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeaderControls() {
        val showRefresh = props.runningOrLoading
        val showDownload = (props.outputState.outputInfo?.status ?: OutputStatus.Missing) != OutputStatus.Missing

        if (showRefresh) {
            Button {
                variant = ButtonVariant.outlined
                size = Size.small
                sx {
//                    borderWidth = 2.px
                    marginLeft = 0.5.em
                    color = NamedColor.black
//                    borderColor = Color("#c4c4c4")
                    borderColor = Color("#777777")
                }

                onClick = {
                    onPreviewRefresh()
                }

                RefreshIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }
                +"Refresh"
            }
        }
        else if (showDownload) {
            val linkAddress = ClientContext.restClient.linkDetachedDownload(
                props.outputStore.mainLocation())

            a {
                css {
                    textDecoration = None.none
                    marginLeft = 0.5.em
                }

                href = linkAddress

                Button {
                    variant = ButtonVariant.outlined
                    size = Size.small
                    sx {
//                        borderWidth = 2.px
                        color = NamedColor.black
//                        borderColor = Color("#c4c4c4")
                        borderColor = Color("#777777")
                    }

                    CloudDownloadIcon::class.react {
                        style = jso {
                            marginRight = 0.25.em
                        }
                    }

                    +"Download"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderOutput() {
        val outputInfo = props.outputState.outputInfo
        val outputPreview = outputInfo?.table?.preview

        div {
            if (outputPreview != null && outputPreview.rows.isNotEmpty()) {
                renderPreviewHeader(outputInfo)
                renderPreviewTable(outputPreview)
            }
            else if (outputInfo != null) {
                renderPreviewHeader(outputInfo)
                renderPreviewTablePlaceholder()
            }
            else {
                renderPreviewTablePlaceholder()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderPreviewHeader(outputInfo: OutputInfo) {
        if (outputInfo.status == OutputStatus.Missing) {
            return
        }

        val outputCount =
            if (props.runningOrLoading) {
                props.progress?.snapshot?.values?.get(ReportConventions.outputTracePath)?.get()?.let { it as Long }
            }
            else {
                props.outputState.outputInfo?.table?.rowCount
            }

        div {
            css {
                width = 100.pct
            }

            div {
                css {
                    display = Display.inlineBlock
                }

                TextAttributeEditor::class.react {
                    key = "start-row"

                    objectLocation = props.outputStore.mainLocation()
                    attributePath = OutputExploreSpec.previewStartPath

                    value = props.spec.explore.previewStart
                    type = TextAttributeEditor.Type.Number

                    labelOverride = "Preview Start Row"

                    onChange = {
                        onPreviewRefresh()
                    }
                }
            }

            div {
                css {
                    display = Display.inlineBlock
                    marginLeft = 1.em
                }

                TextAttributeEditor::class.react {
                    objectLocation = props.outputStore.mainLocation()
                    attributePath = OutputExploreSpec.previewCountPath

                    value = props.spec.explore.previewCount
                    type = TextAttributeEditor.Type.Number

                    labelOverride = "Preview Row Count"

                    onChange = {
                        onPreviewRefresh()
                    }

                    key = "row-count"
                }
            }

            if (outputCount != null) {
                div {
                    css {
                        float = Float.right
                    }

                    div {
                        css {
                            display = Display.inlineBlock
                            marginLeft = 1.em
                        }

                        +"Total rows: ${FormatUtils.decimalSeparator(outputCount)}"
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderPreviewTable(outputPreview: OutputPreview) {
        div {
            css {
                maxHeight = 25.em
                overflowY = Auto.auto
                marginTop = 1.em
                borderWidth = 2.px
                borderStyle = LineStyle.solid
                borderColor = NamedColor.lightgray
            }

            table {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                thead {
                    tr {
                        th {
                            css {
                                position = Position.sticky
                                left = 0.px
                                top = 0.px
                                backgroundColor = NamedColor.white
                                zIndex = integer(1000)
                                width = 2.em
                                height = 2.em
                                textAlign = TextAlign.left
                                boxShadow = BoxShadow(BoxShadowInset.inset, (-2).px, (-2).px, 0.px, 0.px, NamedColor.lightgray)
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                            }
                            +"Row Number"
                        }

                        for (header in outputPreview.header.values) {
                            th {
                                css {
                                    position = Position.sticky
                                    top = 0.px
                                    backgroundColor = NamedColor.white
                                    zIndex = integer(999)
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    textAlign = TextAlign.left
                                    boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-2).px, 0.px, 0.px, NamedColor.lightgray)
                                }
                                key = header
                                +header
                            }
                        }
                    }
                }

                tbody {
                    for (row in outputPreview.rows.withIndex()) {
                        tr {
                            key = row.index.toString()

                            css {
                                hover {
                                    backgroundColor = NamedColor.lightgrey
                                }
                            }

                            td {
                                css {
                                    position = Position.sticky
                                    left = 0.px
                                    backgroundColor = NamedColor.white
                                    zIndex = integer(999)

                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    boxShadow = BoxShadow(BoxShadowInset.inset, (-2).px, 0.px, 0.px, 0.px, NamedColor.lightgray)

                                    if (row.index != 0) {
                                        borderTopWidth = 1.px
                                        borderTopStyle = LineStyle.solid
                                        borderTopColor = NamedColor.lightgray
                                    }
                                }
                                val rowNumber = row.index + outputPreview.startRow.coerceAtLeast(0)
                                val rowFormat = FormatUtils.decimalSeparator(rowNumber + 1)
                                +rowFormat
                            }

                            for (value in row.value.withIndex()) {
                                td {
                                    css {
                                        paddingLeft = 0.5.em
                                        paddingRight = 0.5.em

                                        if (row.index != 0) {
                                            borderTopWidth = 1.px
                                            borderTopStyle = LineStyle.solid
                                            borderTopColor = NamedColor.lightgray
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


    private fun ChildrenBuilder.renderPreviewTablePlaceholder() {
        val filteredColumns = props.filteredColumns
            ?: return

        val headerListing = OutputPreview.emptyHeaderListing(
            filteredColumns, props.analysisSpec)

        div {
            css {
                marginTop = 1.em
                overflowX = Auto.auto
                borderWidth = 2.px
                borderStyle = LineStyle.solid
                borderColor = NamedColor.lightgray
            }

            table {
                css {
                    borderCollapse = BorderCollapse.collapse
                    width = 100.pct
                }

                thead {
                    tr {
                        th {
                            css {
                                width = 2.em
                                height = 2.em
                                textAlign = TextAlign.left
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                            }
                            +"Row Number"
                        }

                        for (header in headerListing.values) {
                            th {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                    textAlign = TextAlign.left
                                }
                                key = header
                                +header
                            }
                        }
                    }
                }
            }
        }
    }
}