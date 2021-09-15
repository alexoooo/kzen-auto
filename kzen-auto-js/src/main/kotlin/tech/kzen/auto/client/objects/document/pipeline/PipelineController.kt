package tech.kzen.auto.client.objects.document.pipeline

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.pipeline.analysis.PipelineAnalysisController
import tech.kzen.auto.client.objects.document.pipeline.filter.PipelineFilterController
import tech.kzen.auto.client.objects.document.pipeline.formula.PipelineFormulaController
import tech.kzen.auto.client.objects.document.pipeline.input.PipelineInputController
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineState
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.objects.document.pipeline.output.PipelineOutputController
import tech.kzen.auto.client.objects.document.pipeline.preview.PipelinePreviewController
import tech.kzen.auto.client.objects.document.pipeline.run.PipelineRunController
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class PipelineController(
    props: react.Props
):
    RPureComponent<react.Props, PipelineController.State>(props),
    PipelineStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val separatorWidth = 2.px
        val separatorColor = Color("#c3c3c3")
        val selectedColor = Color("#e0e0e0")
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface State: react.State {
        var pipelineState: PipelineState?
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

        override fun child(input: RBuilder, handler: RHandler<react.Props>)/*: ReactElement*/ {
            input.child(PipelineController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = PipelineStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: react.Props) {
        pipelineState = null
    }


    override fun componentDidMount() {
        store.didMount(this)
    }


    override fun componentWillUnmount() {
        store.willUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onPipelineState(pipelineState: PipelineState/*, initial: Boolean*/) {
        setState {
            this.pipelineState = pipelineState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val pipelineState = state.pipelineState
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
    private fun RBuilder.renderInput(pipelineState: PipelineState) {
        child(PipelineInputController::class) {
            attrs {
                mainLocation = pipelineState.mainLocation
                spec = pipelineState.inputSpec()
                runningOrLoading = pipelineState.isRunningOrLoading()
                inputState = pipelineState.input
                inputStore = store.input
                progress = pipelineState.run.progress
            }
        }
    }


    private fun RBuilder.renderFormulas(pipelineState: PipelineState) {
        pipelineState.inputAndCalculatedColumns()

        child(PipelineFormulaController::class) {
            attrs {
                formulaSpec = pipelineState.formulaSpec()
                formulaState = pipelineState.formula
                columnListing = pipelineState.input.column.columnListing
                runningOrLoading = pipelineState.isRunningOrLoading()
                formulaStore = store.formula
            }
        }
    }


    private fun RBuilder.renderFilter(pipelineState: PipelineState) {
        child(PipelineFilterController::class) {
            attrs {
                filterSpec = pipelineState.filterSpec()
                runningOrLoading = pipelineState.isRunningOrLoading()
            }
        }
    }


    private fun RBuilder.renderPreview(pipelineState: PipelineState, afterFilter: Boolean) {
        child(PipelinePreviewController::class) {
            attrs {
                previewSpec = pipelineState.previewSpec(afterFilter)
                runningOrLoading = pipelineState.isRunningOrLoading()
                this.afterFilter = afterFilter
                mainLocation = pipelineState.mainLocation
            }
        }
    }


    private fun RBuilder.renderAnalysis(pipelineState: PipelineState) {
        child(PipelineAnalysisController::class) {
            attrs {
                spec = pipelineState.analysisSpec()
                inputAndCalculatedColumns = pipelineState.inputAndCalculatedColumns()
                runningOrLoading = pipelineState.isRunningOrLoading()
                analysisStore = store.analysis
            }
        }
    }


    private fun RBuilder.renderOutput(pipelineState: PipelineState) {
        child(PipelineOutputController::class) {
            attrs {
                spec = pipelineState.outputSpec()
                analysisSpec = pipelineState.analysisSpec()
                inputAndCalculatedColumns = pipelineState.inputAndCalculatedColumns()
                runningOrLoading = pipelineState.isRunningOrLoading()
                progress = pipelineState.run.progress
                outputState = pipelineState.output
                outputStore = store.output
            }
        }
    }


    private fun RBuilder.renderRun(pipelineState: PipelineState) {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(PipelineRunController::class) {
                attrs {
                    this.runState = pipelineState.run
                    this.outputStatus = pipelineState.output.outputInfo?.status ?: OutputStatus.Missing
                    this.pipelineStore = store
                }
            }
        }
    }
}