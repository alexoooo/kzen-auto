package tech.kzen.auto.client.objects.document.report

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.report.analysis.ReportAnalysisController
import tech.kzen.auto.client.objects.document.report.filter.ReportFilterController
import tech.kzen.auto.client.objects.document.report.formula.ReportFormulaController
import tech.kzen.auto.client.objects.document.report.input.ReportInputController
import tech.kzen.auto.client.objects.document.report.model.ReportState
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.objects.document.report.output.ReportOutputController
import tech.kzen.auto.client.objects.document.report.preview.ReportPreviewController
import tech.kzen.auto.client.objects.document.report.run.ReportRunController
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ReportControllerState: react.State {
    var reportState: ReportState?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ReportController(
    props: Props
):
    RPureComponent<Props, ReportControllerState>(props),
    ReportStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val separatorWidth = 2.px
        val separatorColor = Color("#c3c3c3")
        val selectedColor = Color("#e0e0e0")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    ReportController::class.react {
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = ReportStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ReportControllerState.init(props: Props) {
        reportState = null
    }


    override fun componentDidMount() {
        store.didMount(this)
    }


    override fun componentWillUnmount() {
        store.willUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onPipelineState(reportState: ReportState/*, initial: Boolean*/) {
        setState {
            this.reportState = reportState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val reportState = state.reportState
            ?: return

//        renderHeader(processState)

        div {
            css {
                padding = Padding(3.em, 3.em, 7.em, 3.em)
            }

            if (reportState.notationError != null) {
                div {
                    css {
                        margin = Margin(1.em, 1.em, 1.em, 1.em)
                        color = NamedColor.crimson
                        fontWeight = FontWeight.bold
                    }
                    +"Error: ${reportState.notationError}"
                }
            }

            renderInput(reportState)
            renderFormulas(reportState)
//            renderPreview(processState, false)
            renderFilter(reportState)
            renderPreview(reportState, true)
            renderAnalysis(reportState)
            renderOutput(reportState)
        }

        renderRun(reportState)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderHeader(reportState: ReportState) {
//        val isInitiating = reportState.isInitiating()
//        val errorMessage = reportState.nextErrorMessage()
//
//        // NB: placing condition here causes some InputBrowser state to be re-initialized?
//        // if (! isInitiating && errorMessage == null) {
//        //     return
//        // }
//
//        StageController.StageContext.Consumer { context ->
//            if (isInitiating || errorMessage != null) {
//                styledDiv {
//                    css {
//                        position = Position.fixed
//                        top = context.stageTop
//                        left = context.stageLeft
//                        width = 100.pct.minus(context.stageLeft)
//                        zIndex = 99
//                    }
//
//                    if (isInitiating) {
//                        child(MaterialLinearProgress::class) {}
//                    }
//
//                    if (errorMessage != null) {
//                        styledDiv {
//                            css {
//                                backgroundColor = Color.red.lighten(50).withAlpha(0.85)
//                                margin(1.em)
//                                padding(0.25.em)
//                                borderRadius = 3.px
//                                fontWeight = FontWeight.bold
//                            }
//                            +"Error: $errorMessage"
//                        }
//                    }
//                }
//            }
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderInput(reportState: ReportState) {
        ReportInputController::class.react {
            mainLocation = reportState.mainLocation
            spec = reportState.inputSpec()
            runningOrLoading = reportState.isRunningOrLoading()
            inputState = reportState.input
            inputStore = store.input
            progress = reportState.run.progress
        }
    }


    private fun ChildrenBuilder.renderFormulas(reportState: ReportState) {
        reportState.inputAndCalculatedColumns()

        ReportFormulaController::class.react {
            formulaSpec = reportState.formulaSpec()
            formulaState = reportState.formula
            inputColumns = reportState.inputColumnNames()
            runningOrLoading = reportState.isRunningOrLoading()
            formulaStore = store.formula
        }
    }


    private fun ChildrenBuilder.renderFilter(reportState: ReportState) {
        ReportFilterController::class.react {
            filterSpec = reportState.filterSpec()
            runningOrLoading = reportState.isRunningOrLoading()
            filterStore = store.filter
            filterState = reportState.filter
            runningOrLoading = reportState.isRunningOrLoading()
            inputAndCalculatedColumns = reportState.inputAndCalculatedColumns()
            tableSummary = reportState.previewFiltered.tableSummary
        }
    }


    private fun ChildrenBuilder.renderPreview(reportState: ReportState, afterFilter: Boolean) {
        check(afterFilter) { TODO("pipelineState.previewAll") }

        ReportPreviewController::class.react {
            previewSpec = reportState.previewSpec(afterFilter)
            previewState = reportState.previewFiltered
            previewStore = store.previewFiltered
            runningOrLoading = reportState.isRunningOrLoading()
            running = reportState.isRunning()
            this.afterFilter = afterFilter
            mainLocation = reportState.mainLocation
        }
    }


    private fun ChildrenBuilder.renderAnalysis(reportState: ReportState) {
        ReportAnalysisController::class.react {
            spec = reportState.analysisSpec()
            analysisColumnInfo = reportState.analysisColumnInfo()
            inputAndCalculatedColumns = reportState.inputAndCalculatedColumns()
            runningOrLoading = reportState.isRunningOrLoading()
            analysisStore = store.analysis
            inputStore = store.input
            outputStore = store.output
        }
    }


    private fun ChildrenBuilder.renderOutput(reportState: ReportState) {
        ReportOutputController::class.react {
            spec = reportState.outputSpec()
            analysisSpec = reportState.analysisSpec()
            filteredColumns = reportState.filteredColumns()
            runningOrLoading = reportState.isRunningOrLoading()
            progress = reportState.run.progress
            outputState = reportState.output
            outputStore = store.output
        }
    }


    private fun ChildrenBuilder.renderRun(reportState: ReportState) {
        div {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            ReportRunController::class.react {
                thisRunning = reportState.isRunning()
                thisSubmitting = reportState.run.submitting()
                otherRunning = reportState.run.otherRunning

                outputTerminal = (reportState.output.outputInfo?.status ?: OutputStatus.Missing).isTerminal()

                reportStore = store
            }
        }
    }
}