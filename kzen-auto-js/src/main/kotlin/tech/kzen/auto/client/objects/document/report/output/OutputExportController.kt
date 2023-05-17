package tech.kzen.auto.client.objects.document.report.output

import web.cssom.Display
import web.cssom.em
import web.cssom.pct
import emotion.react.css
import js.core.jso
import mui.material.InputAdornment
import mui.material.InputAdornmentPosition
import react.ChildrenBuilder
import react.State
import react.create
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.common.edit.SelectAttributeEditor
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconFile
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec


//---------------------------------------------------------------------------------------------------------------------
external interface OutputExportControllerProps: react.Props {
    var outputExportSpec: OutputExportSpec
    var runningOrLoading: Boolean
    var outputStore: ReportOutputStore
}


//---------------------------------------------------------------------------------------------------------------------
class OutputExportController(
    props: OutputExportControllerProps
):
    RPureComponent<OutputExportControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
//    private fun onFormatChange(format: String) {
//        props.dispatcher.dispatchAsync(ExportFormatRequest(format))
//    }
//
//
//    private fun onCompressionChange(compression: String) {
//        props.dispatcher.dispatchAsync(ExportCompressionRequest(compression))
//    }


//    private fun onRefresh() {
//        props.outputStore.lookupOutputWithFallbackAsync()
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                marginTop = 0.5.em
            }

            renderFormat()
            renderCompression()
        }

        renderPath()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderFormat() {
        div {
            css {
                width = 16.em
                display = Display.inlineBlock
            }

            SelectAttributeEditor::class.react {
                labelOverride = "Format"
                options = OutputExportSpec.formatOptionLabels

                objectLocation = props.outputStore.mainLocation()
                attributePath = OutputExportSpec.formatAttributePath

                value = props.outputExportSpec.format
                disabled = props.runningOrLoading
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderCompression() {
        div {
            css {
                width = 16.em
                display = Display.inlineBlock
                marginLeft = 1.em
            }

            SelectAttributeEditor::class.react {
                labelOverride = "Compression"
                options = OutputExportSpec.compressionOptionLabels

                objectLocation = props.outputStore.mainLocation()
                attributePath = OutputExportSpec.compressionAttributePath

                value = props.outputExportSpec.compression
                disabled = props.runningOrLoading
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderPath() {
        div {
            css {
                marginTop = 1.em
                width = 100.pct
            }

            TextAttributeEditor::class.react {
                labelOverride = "Export Path Pattern"

                InputProps = jso {
                    startAdornment = InputAdornment.create {
                        position = InputAdornmentPosition.start
                        iconify(vaadinIconFile)
                    }
                }

                objectLocation = props.outputStore.mainLocation()
                attributePath = OutputExportSpec.pathAttributePath

                value = props.outputExportSpec.pathPattern
                type = TextAttributeEditor.Type.PlainText
                disabled = props.runningOrLoading
            }
        }
    }
}