package tech.kzen.auto.client.objects.document.report.preview

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.common.edit.BooleanAttributeEditor
import tech.kzen.auto.client.objects.document.report.preview.model.ReportPreviewState
import tech.kzen.auto.client.objects.document.report.preview.model.ReportPreviewStore
import tech.kzen.auto.client.objects.document.report.widget.ReportBottomEgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconInfoCircleO
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.material.VisibilityIcon
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


//-----------------------------------------------------------------------------------------------------------------
external interface ReportPreviewControllerProps: react.Props {
    var previewSpec: PreviewSpec
    var previewState: ReportPreviewState
    var previewStore: ReportPreviewStore
    var runningOrLoading: Boolean
    var running: Boolean
    var afterFilter: Boolean
    var mainLocation: ObjectLocation
}


//-----------------------------------------------------------------------------------------------------------------
class ReportPreviewController(
    props: ReportPreviewControllerProps
):
    RPureComponent<ReportPreviewControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        props.previewStore.lookupSummaryWithFallbackAsync()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun PropertiesBuilder.commonThCss() {
        position = Position.sticky
        top = 0.px
        backgroundColor = NamedColor.white
        zIndex = integer(999)
        textAlign = TextAlign.left
        boxShadow = BoxShadow(BoxShadowInset.inset, 0.px, (-2).px, 0.px, 0.px, NamedColor.lightgray)
        paddingLeft = 0.5.em
        paddingRight = 0.5.em
    }


    private fun PropertiesBuilder.commonTdCss(isFirst: Boolean) {
        paddingLeft = 0.5.em
        paddingRight = 0.5.em

        if (! isFirst) {
            borderTopWidth = 1.px
            borderTopStyle = LineStyle.solid
            borderTopColor = NamedColor.lightgray
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                height = 100.pct
                marginTop = 5.px
            }

            div {
                css {
                    borderRadius = 3.px
                    backgroundColor = NamedColor.white
                    width = 100.pct
                }

                div {
                    css {
                        padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                    }

                    renderContent()
                }
            }

            ReportBottomEgress::class.react {
                this.egressColor = NamedColor.white
                parentWidth = 100.pct
            }
        }
    }


    private fun ChildrenBuilder.renderContent() {
        renderHeader()
        renderBody()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeader() {
        div {
            span {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                VisibilityIcon::class.react {
                    style = jso {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-17).px
                        left = (-3).px
                    }
                }
            }

            span {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Preview"
            }

            span {
                css {
                    float = Float.right
                }

                div {
                    css {
                        display = Display.inlineBlock
                        marginRight = 1.em
                    }
                    renderRefresh()
                }

                div {
                    css {
                        display = Display.inlineBlock
                    }
                    renderEnable()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRefresh() {
        if (props.previewState.tableSummary == null ||
               ! props.running
        ) {
            return
        }

        Button {
            variant = ButtonVariant.outlined
            size = Size.small

            onClick = {
                onRefresh()
            }

            sx {
                marginLeft = 0.5.em
                color = NamedColor.black
                borderColor = Color("#777777")
            }

            RefreshIcon::class.react {
                style = jso {
                    marginRight = 0.25.em
                }
            }
            +"Refresh"
        }
    }


    private fun ChildrenBuilder.renderEnable() {
        BooleanAttributeEditor::class.react {
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderBody() {
        renderError()
        renderInfo()
        renderPreview()
    }


    private fun ChildrenBuilder.renderError() {
        val error = props.previewState.previewError
            ?: return

        div {
            css {
                color = NamedColor.red
            }

            +error
        }
    }


    private fun ChildrenBuilder.renderInfo() {
        val enabled = props.previewSpec.enabled
        val present = props.previewState.tableSummary?.isEmpty() == false

        if (enabled && present || enabled && props.running) {
            return
        }

        div {
            span {
                css {
                    fontSize = 1.25.em
                    fontStyle = FontStyle.italic
                    color = NamedColor.darkgray
                }

                span {
                    css {
                        marginRight = 0.25.em
                    }
                    iconify(vaadinIconInfoCircleO)
                }

                if (! enabled) {
                    +"Enable for suggestions to appear in Filter"
                }
                else {
                    +"Run to populate preview data"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderPreview() {
        val tableSummary = props.previewState.tableSummary
            ?: return

        div {
            css {
                width = 100.pct
                maxHeight = 20.em
                overflowY = Auto.auto
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
                                textAlign = TextAlign.left
                                boxShadow = BoxShadow(BoxShadowInset.inset, (-2).px, (-2).px, 0.px, 0.px, NamedColor.lightgray)
                                paddingLeft = 0.5.em
                                paddingRight = 0.5.em
                            }
                            +"Column"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            +"Non-empty Count"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            +"Number Count"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            +"Sum"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            +"Min"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            +"Max"
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            div {
                                css {
                                    width = 40.em
                                }
                                +"Histogram"
                            }
                        }
                        th {
                            css {
                                commonThCss()
                            }
                            div {
                                css {
                                    width = 45.em
                                }
                                +"Sample"
                            }
                        }
                    }
                }
                tbody {
                    var isFirst = true
                    for ((columnName, columnSummary) in tableSummary.columnSummaries) {
                        tr {
                            key = columnName

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
                                    boxShadow = BoxShadow(BoxShadowInset.inset, (-2).px, 0.px, 0.px, 0.px, NamedColor.lightgray)
                                    commonTdCss(isFirst)
                                }
                                +columnName
                            }

                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (! columnSummary.isEmpty()) {
                                    +"${columnSummary.count}"
                                }
                            }

                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.count}"
                                }
                            }
                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.sum}"
                                }
                            }
                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.min}"
                                }
                            }
                            td {
                                css {
                                    commonTdCss(isFirst)
                                }
                                if (columnSummary.numericValueSummary.count > 0) {
                                    +"${columnSummary.numericValueSummary.max}"
                                }
                            }

                            td {
                                css {
                                    commonTdCss(isFirst)
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

                            td {
                                css {
                                    commonTdCss(isFirst)
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

                        isFirst = false
                    }
                }
            }
        }
    }
}