package tech.kzen.auto.client.objects.document.pipeline.output

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputState
import tech.kzen.auto.client.objects.document.pipeline.output.model.PipelineOutputStore
import tech.kzen.auto.client.objects.document.pipeline.run.model.PipelineRunProgress
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconTable
import tech.kzen.auto.client.wrap.iconify.vaadinIconUploadAlt
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType


class PipelineOutputController(
    props: Props
):
    RPureComponent<PipelineOutputController.Props, PipelineOutputController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val iconWithPadding = 2.5.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var spec: OutputSpec
        var analysisSpec: AnalysisSpec
        var inputAndCalculatedColumns: HeaderListing?
        var runningOrLoading: Boolean
        var progress: PipelineRunProgress?
        var outputState: PipelineOutputState
        var outputStore: PipelineOutputStore
    }


    interface State: react.State {
        var settingsOpen: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        settingsOpen = ! props.spec.isDefaultWorkPath()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSettingsToggle() {
        setState {
            settingsOpen = ! settingsOpen
        }
    }


    private fun onRefresh() {
        props.outputStore.lookupOutputWithFallbackAsync()
    }


    private fun onTypeChange(outputType: OutputType) {
        props.outputStore.setOutputTypeAsync(outputType)
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
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderContent() {
        renderHeader()

        if (! props.inputAndCalculatedColumns?.values.isNullOrEmpty()) {
            renderBody()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            css {
                width = 100.pct
                position = Position.relative
            }

            styledSpan {
                css {
                    height = 2.em
                    width = iconWithPadding
                    position = Position.relative
                }

                child(SaveAltIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-0.5).em
                            left = (-3.5).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = iconWithPadding.div(2)
                    fontSize = 2.em
                }

                +"Output"
            }

            styledSpan {
                css {
//                    marginLeft = iconWithPadding.div(2)
                    marginLeft = 1.em
                    fontSize = 1.5.em
                    fontStyle = FontStyle.italic
                }

                val status = props.outputState.outputInfo?.status ?: OutputStatus.Missing
                if (status == OutputStatus.Missing) {
                    if (props.inputAndCalculatedColumns?.values.isNullOrEmpty()) {
                        +"Select input (top of page)"
                    }
                    else {
                        +"Run report (bottom right)"
                    }
                }
                else {
                    +"Status: ${status.name}"
                }
            }

            styledSpan {
                css {
                    float = Float.right
                }

                renderHeaderControls()
            }
        }
    }


    private fun RBuilder.renderHeaderControls() {
//        val editDisabled = props.reportState.isTaskRunning() || props.reportState.isInitiating()

        child(MaterialToggleButtonGroup::class) {
            attrs {
                value = props.spec.type.name
                exclusive = true
                onChange = { _, v ->
                    if (v is String) {
                        onTypeChange(OutputType.valueOf(v))
                    }
                }
            }

            child(MaterialToggleButton::class) {
                attrs {
                    value = OutputType.Explore.name
                    disabled = props.runningOrLoading
                    size = "medium"
                    style = reactStyle {
                        height = 34.px
                        color = Color.black
                        borderWidth = 2.px
                    }
                }

                styledSpan {
                    css {
                        fontSize = 1.5.em
                        marginRight = 0.25.em
                        marginBottom = (-0.25).em
                    }
                    iconify(vaadinIconTable)
                }

                +"Table"
            }

            child(MaterialToggleButton::class) {
                attrs {
                    value = OutputType.Export.name
                    disabled = props.runningOrLoading
                    size = "medium"
                    style = reactStyle {
                        height = 34.px
                        color = Color.black
                        borderWidth = 2.px
                    }
                }

                styledSpan {
                    css {
                        fontSize = 1.5.em
                        marginRight = 0.25.em
                    }
                    iconify(vaadinIconUploadAlt)
                }

                +"Export"
            }
        }

        child(MaterialButton::class) {
            attrs {
                title = "Settings"
                variant = "outlined"
                size = "small"

                onClick = {
                    onSettingsToggle()
                }

                style = reactStyle {
                    marginLeft = 1.em
                    borderWidth = 2.px
                    marginTop = (-10).px

                    if (state.settingsOpen) {
                        backgroundColor = ReportController.selectedColor
                    }
                }
            }

            child(SettingsIcon::class) {}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBody() {
        val error = props.outputState.outputInfoError

        renderError(error)

        renderSettings()

        renderOutputType()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderError(error: String?) {
        if (error == null) {
            return
        }

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


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOutputType() {
        val type = props.spec.type

        styledDiv {
            when (type) {
                OutputType.Explore ->
                    renderTable()

                OutputType.Export ->
                    renderExport()
            }
        }
    }


    private fun RBuilder.renderTable() {
        child(OutputTableController::class) {
            attrs {
                spec = props.spec
                analysisSpec = props.analysisSpec
                inputAndCalculatedColumns = props.inputAndCalculatedColumns
                runningOrLoading = props.runningOrLoading
                progress = props.progress
                outputState = props.outputState
                outputStore = props.outputStore
            }
        }
    }


    private fun RBuilder.renderExport() {
        child(OutputExportController::class) {
            attrs {
                outputExportSpec = props.spec.export
                runningOrLoading = props.runningOrLoading
                outputStore = props.outputStore
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSettings() {
        if (! state.settingsOpen) {
            return
        }

        val outputInfo = props.outputState.outputInfo
            ?: return

        styledDiv {
            css {
                marginBottom = 1.em
                width = 100.pct
                paddingBottom = 1.em
                borderBottomWidth = 2.px
                borderBottomStyle = BorderStyle.solid
                borderBottomColor = Color.lightGray
            }


            child(TextAttributeEditor::class) {
                attrs {
                    labelOverride = "Report Work Folder"

                    objectLocation = props.outputStore.mainLocation()
                    attributePath = OutputSpec.workDirPath

                    value = props.spec.workPath
                    type = TextAttributeEditor.Type.PlainText

                    onChange = {
                        onRefresh()
                    }

                    disabled = props.runningOrLoading
                }
            }

            +"Absolute path: ${outputInfo.runDir}"
        }
    }
}