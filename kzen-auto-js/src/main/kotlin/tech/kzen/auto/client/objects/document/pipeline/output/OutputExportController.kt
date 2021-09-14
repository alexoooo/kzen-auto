package tech.kzen.auto.client.objects.document.pipeline.output

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import react.buildElement
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.common.edit.SelectAttributeEditor
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputStore
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconFile
import tech.kzen.auto.client.wrap.material.MaterialInputAdornment
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec


class OutputExportController(
    props: Props
):
    RPureComponent<OutputExportController.Props, OutputExportController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var outputExportSpec: OutputExportSpec
        var runningOrLoading: Boolean
        var outputStore: PipelineOutputStore
    }


    interface State: react.State {
//        var settingsOpen: Boolean
    }


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
    override fun RBuilder.render() {
        styledDiv {
            css {
                marginTop = 0.5.em
            }

            renderFormat()
            renderCompression()
        }

        renderPath()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderFormat() {
        styledDiv {
            css {
                width = 16.em
                display = Display.inlineBlock
            }

            child(SelectAttributeEditor::class) {
                attrs {
                    labelOverride = "Format"
                    options = OutputExportSpec.formatOptionLabels

                    objectLocation = props.outputStore.mainLocation()
                    attributePath = OutputExportSpec.formatAttributePath

                    value = props.outputExportSpec.format
                    disabled = props.runningOrLoading
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderCompression() {
        styledDiv {
            css {
                width = 16.em
                display = Display.inlineBlock
                marginLeft = 1.em
            }

            child(SelectAttributeEditor::class) {
                attrs {
                    labelOverride = "Compression"
                    options = OutputExportSpec.compressionOptionLabels

                    objectLocation = props.outputStore.mainLocation()
                    attributePath = OutputExportSpec.compressionAttributePath

                    value = props.outputExportSpec.compression
                    disabled = props.runningOrLoading
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderPath() {
        styledDiv {
            css {
                marginTop = 1.em
                width = 100.pct
            }

            child(TextAttributeEditor::class) {
                attrs {
                    labelOverride = "Export Path Pattern"

                    InputProps = object : react.Props {
                        @Suppress("unused")
                        var startAdornment = buildElement {
                            child(MaterialInputAdornment::class) {
                                attrs {
                                    position = "start"
                                }
                                iconify(vaadinIconFile)
                            }
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
}