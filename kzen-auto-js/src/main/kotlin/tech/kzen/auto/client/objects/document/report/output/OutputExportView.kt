package tech.kzen.auto.client.objects.document.report.output

import kotlinx.browser.document
import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.report.state.ExportCompressionRequest
import tech.kzen.auto.client.objects.document.report.state.ExportFormatRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import kotlin.js.Json
import kotlin.js.json


class OutputExportView(
    props: Props
):
    RPureComponent<OutputExportView.Props, OutputExportView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
    }


    interface State: react.State


    //-----------------------------------------------------------------------------------------------------------------
    private fun onFormatChange(format: String) {
        props.dispatcher.dispatchAsync(ExportFormatRequest(format))
    }


    private fun onCompressionChange(compression: String) {
        props.dispatcher.dispatchAsync(ExportCompressionRequest(compression))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        renderFormat()
        renderCompression()

        styledDiv {
            child(OutputExportPath::class) {
                attrs {
                    reportState = props.reportState
                    dispatcher = props.dispatcher
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderFormat() {
        val selectId = "material-react-format"

        styledDiv {
            css {
                width = 16.em
                display = Display.inlineBlock
            }

            child(MaterialInputLabel::class) {
                attrs {
                    htmlFor = selectId

                    style = reactStyle {
                        fontSize = 0.8.em
                        width = 16.em
                    }
                }

                +"Format"
            }

            val format = props.reportState.outputSpec().export.format
            val selectedOption = ReactSelectOption(format, format)

            val formatOptions = OutputExportSpec.formatOptions.map {
                ReactSelectOption(it, it)
            }

            val selectOptions = formatOptions.toTypedArray()

            child(ReactSelect::class) {
                attrs {
                    id = selectId

//                value = selectOptions.find { it.value == state.selectedColumn }
                    value = selectedOption

                    options = selectOptions
//                options = optionsArray

                    onChange = {
                        onFormatChange(it.value)
                    }

//                isDisabled = props.editDisabled

                    // https://stackoverflow.com/a/51844542/1941359
                    val styleTransformer: (Json, Json) -> Json = { base, _ ->
                        val transformed = json()
                        transformed.add(base)
                        transformed["background"] = "transparent"
                        transformed["borderWidth"] = "2px"
                        transformed
                    }

                    val reactStyles = json()
                    reactStyles["control"] = styleTransformer
                    styles = reactStyles

                    menuPortalTarget = document.body!!
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderCompression() {
        val selectId = "material-react-compression"

        styledDiv {
            css {
                width = 16.em
                display = Display.inlineBlock
                marginLeft = 1.em
            }

            child(MaterialInputLabel::class) {
                attrs {
                    htmlFor = selectId

                    style = reactStyle {
                        fontSize = 0.8.em
                        width = 16.em
                    }
                }

                +"Compression"
            }

            val compression = props.reportState.outputSpec().export.compression
            val selectedOption = ReactSelectOption(compression, compression)

            val compressionOptions = OutputExportSpec.compressionOptions.map {
                ReactSelectOption(it, it)
            }

            val selectOptions = compressionOptions.toTypedArray()

            child(ReactSelect::class) {
                attrs {
                    id = selectId

//                value = selectOptions.find { it.value == state.selectedColumn }
                    value = selectedOption

                    options = selectOptions
//                options = optionsArray

                    onChange = {
                        onCompressionChange(it.value)
                    }

//                onMenuOpen = {
//                    loadIfRequired()
//                }

//                isDisabled = props.editDisabled

                    // https://stackoverflow.com/a/51844542/1941359
                    val styleTransformer: (Json, Json) -> Json = { base, _ ->
                        val transformed = json()
                        transformed.add(base)
                        transformed["background"] = "transparent"
                        transformed["borderWidth"] = "2px"
                        transformed
                    }

                    val reactStyles = json()
                    reactStyles["control"] = styleTransformer
                    styles = reactStyles

                    menuPortalTarget = document.body!!
                }
            }
        }
    }
}