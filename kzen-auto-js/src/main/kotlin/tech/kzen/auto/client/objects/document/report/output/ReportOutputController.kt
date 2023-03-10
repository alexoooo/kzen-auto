package tech.kzen.auto.client.objects.document.report.output

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.*
import mui.material.Size
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputState
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunProgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconTable
import tech.kzen.auto.client.wrap.iconify.vaadinIconUploadAlt
import tech.kzen.auto.client.wrap.material.SaveAltIcon
import tech.kzen.auto.client.wrap.material.SettingsIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType


//---------------------------------------------------------------------------------------------------------------------
external interface ReportOutputControllerProps: react.Props {
    var spec: OutputSpec
    var analysisSpec: AnalysisSpec
    var filteredColumns: HeaderListing?
    var runningOrLoading: Boolean
    var progress: ReportRunProgress?
    var outputState: ReportOutputState
    var outputStore: ReportOutputStore
}


external interface ReportOutputControllerState: react.State {
    var settingsOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class ReportOutputController(
    props: ReportOutputControllerProps
):
    RPureComponent<ReportOutputControllerProps, ReportOutputControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val iconWithPadding = 2.5.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ReportOutputControllerState.init(props: ReportOutputControllerProps) {
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
                        padding = 0.5.em
                    }

                    renderContent()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderContent() {
        renderHeader()

        if (! props.filteredColumns?.values.isNullOrEmpty()) {
            renderBody()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeader() {
        div {
            css {
                width = 100.pct
                position = Position.relative
            }

            span {
                css {
                    height = 2.em
                    width = iconWithPadding
                    position = Position.relative
                }

                SaveAltIcon::class.react {
                    style = jso {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-0.5).em
                        left = (-3.5).px
                    }
                }
            }

            span {
                css {
                    marginLeft = iconWithPadding.div(2)
                    fontSize = 2.em
                }

                +"Output"
            }

            span {
                css {
//                    marginLeft = iconWithPadding.div(2)
                    marginLeft = 1.em
                    fontSize = 1.5.em
                    fontStyle = FontStyle.italic
                }

                val status = props.outputState.outputInfo?.status ?: OutputStatus.Missing
                if (status == OutputStatus.Missing) {
                    if (props.filteredColumns?.values.isNullOrEmpty()) {
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

            span {
                css {
                    float = Float.right
                }

                renderHeaderControls()
            }
        }
    }


    private fun ChildrenBuilder.renderHeaderControls() {
//        val editDisabled = props.reportState.isTaskRunning() || props.reportState.isInitiating()

        ToggleButtonGroup {
            value = props.spec.type.name
            exclusive = true
            onChange = { _, v ->
                if (v is String) {
                    @Suppress("UnsafeCastFromDynamic")
                    onTypeChange(OutputType.valueOf(v))
                }
            }

            ToggleButton {
                value = OutputType.Explore.name
                disabled = props.runningOrLoading
                size = Size.medium
                css {
                    height = 34.px
                    color = NamedColor.black
                    borderWidth = 2.px
                }

                span {
                    css {
                        fontSize = 1.5.em
                        marginRight = 0.25.em
                        marginBottom = (-0.25).em
                    }
                    iconify(vaadinIconTable)
                }

                +"Table"
            }
            ToggleButton {
                value = OutputType.Export.name
                disabled = props.runningOrLoading
                size = Size.medium
                css {
                    height = 34.px
                    color = NamedColor.black
                    borderWidth = 2.px
                }

                span {
                    css {
                        fontSize = 1.5.em
                        marginRight = 0.25.em
                    }
                    iconify(vaadinIconUploadAlt)
                }

                +"Export"
            }
        }

        Button {
            title = "Settings"
            variant = ButtonVariant.outlined
            size = Size.small

            onClick = {
                onSettingsToggle()
            }

            css {
                marginLeft = 1.em
                borderWidth = 2.px
                marginTop = (-10).px

                if (state.settingsOpen) {
                    backgroundColor = ReportController.selectedColor
                }
                color = NamedColor.black
                borderColor = Color("#c4c4c4")
            }

            SettingsIcon::class.react {}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderBody() {
        val error = props.outputState.outputInfoError

        renderError(error)

        renderSettings()

        renderOutputType()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderError(error: String?) {
        if (error == null) {
            return
        }

        div {
            css {
                marginBottom = 1.em
                width = 100.pct
                paddingBottom = 1.em
                borderBottomWidth = 2.px
                borderBottomStyle = LineStyle.solid
                borderBottomColor = NamedColor.lightgray
            }
            +"Error: $error"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderOutputType() {
        val type = props.spec.type

        div {
            when (type) {
                OutputType.Explore ->
                    renderTable()

                OutputType.Export ->
                    renderExport()
            }
        }
    }


    private fun ChildrenBuilder.renderTable() {
        OutputTableController::class.react {
            spec = props.spec
            analysisSpec = props.analysisSpec
            filteredColumns = props.filteredColumns
            runningOrLoading = props.runningOrLoading
            progress = props.progress
            outputState = props.outputState
            outputStore = props.outputStore
        }
    }


    private fun ChildrenBuilder.renderExport() {
        OutputExportController::class.react {
            outputExportSpec = props.spec.export
            runningOrLoading = props.runningOrLoading
            outputStore = props.outputStore
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSettings() {
        if (! state.settingsOpen) {
            return
        }

        val outputInfo = props.outputState.outputInfo
            ?: return

        div {
            css {
                marginBottom = 1.em
                width = 100.pct
                paddingBottom = 1.em
                borderBottomWidth = 2.px
                borderBottomStyle = LineStyle.solid
                borderBottomColor = NamedColor.lightgray
            }


            TextAttributeEditor::class.react {
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

            +"Absolute path: ${outputInfo.runDir}"
        }
    }
}