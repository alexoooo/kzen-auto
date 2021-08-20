package tech.kzen.auto.client.objects.document.pipeline

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.pipeline.input.PipelineInputController
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineState
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.objects.document.pipeline.run.PipelineRunController
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class PipelineController(
    props: RProps
):
    RPureComponent<RProps, PipelineController.State>(props),
    PipelineStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val separatorWidth = 2.px
//        val separatorColor = Color("#c3c3c3")
//        val selectedColor = Color("#e0e0e0")
//    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
        var pipelineState: PipelineState?
    ): RState


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

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(PipelineController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = PipelineStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: RProps) {
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
//            renderFormulas(processState)
////            renderPreview(processState, false)
//            renderFilter(processState)
//            renderPreview(processState, true)
//            renderAnalysis(processState)
//            renderOutput(processState)
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
//    private fun RBuilder.renderInput(reportState: ReportState) {
    private fun RBuilder.renderInput(pipelineState: PipelineState) {
        child(PipelineInputController::class) {
            attrs {
                mainLocation = pipelineState.mainLocation
                spec = pipelineState.inputSpec()
                inputState = pipelineState.input
                inputStore = store.input
                progress = pipelineState.run.progress
//                this.reportState = reportState
//                dispatcher = store
            }
        }
    }

//
//
//    private fun RBuilder.renderFormulas(reportState: ReportState) {
//        child(ReportFormulaList::class) {
//            attrs {
//                this.reportState = reportState
//                this.dispatcher = store
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderFilter(reportState: ReportState) {
//        child(ReportFilterList::class) {
//            attrs {
//                this.reportState = reportState
//                this.dispatcher = store
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderPreview(reportState: ReportState, afterFilter: Boolean) {
//        child(PreviewView::class) {
//            attrs {
//                this.reportState = reportState
//                this.dispatcher = store
//                this.afterFilter = afterFilter
//            }
//        }
//    }
//
//
////    private fun RBuilder.renderPivot(reportState: ReportState) {
////        child(ReportPivot::class) {
////            attrs {
////                this.reportState = reportState
////                this.dispatcher = store
////            }
////        }
////    }
//
//
//    private fun RBuilder.renderAnalysis(reportState: ReportState) {
//        child(AnalysisView::class) {
//            attrs {
//                this.reportState = reportState
//                this.dispatcher = store
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderOutput(reportState: ReportState) {
//        child(ReportOutputView::class) {
//            attrs {
//                this.reportState = reportState
//                this.dispatcher = store
//            }
//        }
//    }


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
                    this.pipelineState = pipelineState
                    this.pipelineStore = store
                }
            }
        }
    }
}