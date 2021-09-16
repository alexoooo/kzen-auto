package tech.kzen.auto.client.objects.document.pipeline.preview

import kotlinx.css.*
import kotlinx.css.properties.boxShadowInset
import react.RBuilder
import react.RPureComponent
import react.dom.tbody
import react.dom.thead
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.common.edit.BooleanAttributeEditor
import tech.kzen.auto.client.objects.document.pipeline.preview.model.PipelinePreviewState
import tech.kzen.auto.client.objects.document.pipeline.preview.model.PipelinePreviewStore
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.wrap.material.MaterialButton
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.material.VisibilityIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class PipelinePreviewController(
    props: Props
):
    RPureComponent<PipelinePreviewController.Props, PipelinePreviewController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var previewSpec: PreviewSpec
        var previewState: PipelinePreviewState
        var previewStore: PipelinePreviewStore
        var runningOrLoading: Boolean
        var running: Boolean
        var afterFilter: Boolean
        var mainLocation: ObjectLocation
    }


    interface State: react.State


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        props.previewStore.lookupSummaryWithFallbackAsync()
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

            child(ReportBottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderBody()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                child(VisibilityIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-17).px
                            left = (-3).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Preview"
            }

            styledSpan {
                css {
                    float = Float.right
                }

                styledDiv {
                    css {
                        display = Display.inlineBlock
                        marginRight = 1.em
                    }
                    renderRefresh()
                }

                styledDiv {
                    css {
                        display = Display.inlineBlock
                    }
                    renderEnable()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderRefresh() {
        if (props.previewState.tableSummary == null ||
               ! props.running
        ) {
            return
        }

        child(MaterialButton::class) {
            attrs {
                variant = "outlined"
                size = "small"

                onClick = {
                    onRefresh()
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


    private fun RBuilder.renderEnable() {
        child(BooleanAttributeEditor::class) {
            attrs {
                trueLabelOverride = "Enabled"
                falseLabelOverride = "Disabled"

                objectLocation = props.mainLocation
                attributePath = PreviewSpec.enabledAttributePath(props.afterFilter)

                value = props.previewSpec.enabled
                disabled = props.runningOrLoading

                onChange = {
                    onRefresh()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBody() {
        renderError()
        renderInfo()
        renderPreview()
    }


    private fun RBuilder.renderError() {
        val error = props.previewState.previewError
            ?: return

        styledDiv {
            css {
                color = Color.red
            }

            +error
        }
    }


    private fun RBuilder.renderInfo() {
        styledDiv {
            //child(InfoIcon::class) {}

            styledSpan {
                css {
                    fontSize = 1.25.em
                    fontStyle = FontStyle.italic
                }
                +"Must be enabled for suggestions to appear in Filter"
            }
        }
    }


    private fun RBuilder.renderPreview() {
        val tableSummary = props.previewState.tableSummary
            ?: return

        styledDiv {
            css {
                width = 100.pct
                maxHeight = 20.em
                overflowY = Overflow.auto
                borderWidth = 2.px
                borderStyle = BorderStyle.solid
                borderColor = Color.lightGray
            }

            styledTable {
                thead {
                    tr {
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Column"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Non-empty Count"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Number Count"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Sum"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Min"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            +"Max"
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            styledDiv {
                                css {
                                    width = 40.em
                                }
                                +"Histogram"
                            }
                        }
                        styledTh {
                            css {
                                position = Position.sticky
                                top = 0.px
                                backgroundColor = Color.white
                                zIndex = 999
                                textAlign = TextAlign.left
                                boxShadowInset(Color.lightGray, 0.px, (-1).px, 0.px, 0.px)
                            }
                            styledDiv {
                                css {
                                    width = 45.em
                                }
                                +"Sample"
                            }
                        }
                    }
                }
                tbody {
                    for ((columnName, columnSummary) in tableSummary.columnSummaries) {
                        tr {
                            key = columnName

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                +columnName
                            }

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (! columnSummary.isEmpty()) {
                                    +"${columnSummary.count}"
                                }
                            }

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.count}"
                                }
                            }
                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.sum}"
                                }
                            }
                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.min}"
                                }
                            }
                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.max}"
                                }
                            }

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (! columnSummary.nominalValueSummary.isEmpty()) {
                                    if (columnSummary.nominalValueSummary.histogram.size > 5) {
                                        +"${columnSummary.nominalValueSummary.histogram.size} categories: "
                                    }

                                    +columnSummary
                                        .nominalValueSummary
                                        .histogram
                                        .entries
                                        .sortedByDescending { it.value }
                                        .take(5)
                                        .joinToString {
                                            if (it.key.isBlank()) {
                                                "(blank) = ${it.value}"
                                            } else {
                                                "${it.key} = ${it.value}"
                                            }
                                        }
                                }
                            }

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                    paddingRight = 0.5.em
                                }
                                if (! columnSummary.opaqueValueSummary.isEmpty()) {
                                    +columnSummary
                                        .opaqueValueSummary
                                        .sample
                                        .take(10)
                                        .joinToString()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}