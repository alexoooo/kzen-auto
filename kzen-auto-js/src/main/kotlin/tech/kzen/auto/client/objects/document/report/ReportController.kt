package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
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
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class ReportController(
    props: react.Props
):
    RPureComponent<react.Props, ReportController.State>(props),
    ReportStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val separatorWidth = 2.px
        val separatorColor = Color("#c3c3c3")
        val selectedColor = Color("#e0e0e0")
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface State: react.State {
        var reportState: ReportState?
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
                override fun child(input: RBuilder, handler: RHandler<Props>) {
//                    with (input) {
//                        +"... foo ..."
//                    }
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun child(input: RBuilder, handler: RHandler<Props>) {
                    input.child(ReportController::class) {
                        handler()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = ReportStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: react.Props) {
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
    override fun RBuilder.render() {
        val pipelineState = state.reportState
            ?: return

//        renderHeader(processState)

        styledDiv {
            css {
                padding(3.em, 3.em, 7.em, 3.em)
            }

            if (pipelineState.notationError != null) {
                styledDiv {
                    css {
                        margin(1.em)
                        color =  Color.crimson
                        fontWeight = FontWeight.bold
                    }
                    +"Error: ${pipelineState.notationError}"
                }
            }

            renderInput(pipelineState)
            renderFormulas(pipelineState)
//            renderPreview(processState, false)
            renderFilter(pipelineState)
            renderPreview(pipelineState, true)
            renderAnalysis(pipelineState)
            renderOutput(pipelineState)
        }

        renderRun(pipelineState)
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
    private fun RBuilder.renderInput(reportState: ReportState) {
        child(ReportInputController::class) {
            attrs {
                mainLocation = reportState.mainLocation
                spec = reportState.inputSpec()
                runningOrLoading = reportState.isRunningOrLoading()
                inputState = reportState.input
                inputStore = store.input
                progress = reportState.run.progress
            }
        }
    }


    private fun RBuilder.renderFormulas(reportState: ReportState) {
        reportState.inputAndCalculatedColumns()

        child(ReportFormulaController::class) {
            attrs {
                formulaSpec = reportState.formulaSpec()
                formulaState = reportState.formula
                inputColumns = reportState.inputColumnNames()
                runningOrLoading = reportState.isRunningOrLoading()
                formulaStore = store.formula
            }
        }
    }


    private fun RBuilder.renderFilter(reportState: ReportState) {
        child(ReportFilterController::class) {
            attrs {
                filterSpec = reportState.filterSpec()
                runningOrLoading = reportState.isRunningOrLoading()
                filterStore = store.filter
                filterState = reportState.filter
                runningOrLoading = reportState.isRunningOrLoading()
                inputAndCalculatedColumns = reportState.inputAndCalculatedColumns()
                tableSummary = reportState.previewFiltered.tableSummary
            }
        }
    }


    private fun RBuilder.renderPreview(reportState: ReportState, afterFilter: Boolean) {
        check(afterFilter) { TODO("pipelineState.previewAll") }

        child(ReportPreviewController::class) {
            attrs {
                previewSpec = reportState.previewSpec(afterFilter)
                previewState = reportState.previewFiltered
                previewStore = store.previewFiltered
                runningOrLoading = reportState.isRunningOrLoading()
                running = reportState.isRunning()
                this.afterFilter = afterFilter
                mainLocation = reportState.mainLocation
            }
        }
    }


    private fun RBuilder.renderAnalysis(reportState: ReportState) {
        child(ReportAnalysisController::class) {
            attrs {
                spec = reportState.analysisSpec()
                analysisColumnInfo = reportState.analysisColumnInfo()
                inputAndCalculatedColumns = reportState.inputAndCalculatedColumns()
                runningOrLoading = reportState.isRunningOrLoading()
                analysisStore = store.analysis
                inputStore = store.input
                outputStore = store.output
            }
        }
    }


    private fun RBuilder.renderOutput(reportState: ReportState) {
        child(ReportOutputController::class) {
            attrs {
                spec = reportState.outputSpec()
                analysisSpec = reportState.analysisSpec()
                filteredColumns = reportState.filteredColumns()
                runningOrLoading = reportState.isRunningOrLoading()
                progress = reportState.run.progress
                outputState = reportState.output
                outputStore = store.output
            }
        }
    }


    private fun RBuilder.renderRun(reportState: ReportState) {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(ReportRunController::class) {
                attrs {
                    thisRunning = reportState.isRunning()
                    thisSubmitting = reportState.run.submitting()
                    otherRunning = reportState.run.otherRunning

                    outputTerminal = (reportState.output.outputInfo?.status ?: OutputStatus.Missing).isTerminal()

                    reportStore = store
                }
            }
        }
    }
}