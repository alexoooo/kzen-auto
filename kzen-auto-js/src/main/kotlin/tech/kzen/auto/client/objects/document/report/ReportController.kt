package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.document.report.analysis.AnalysisView
import tech.kzen.auto.client.objects.document.report.filter.ReportFilterList
import tech.kzen.auto.client.objects.document.report.formula.ReportFormulaList
import tech.kzen.auto.client.objects.document.report.input.ReportInputView
import tech.kzen.auto.client.objects.document.report.output.ReportOutputView2
import tech.kzen.auto.client.objects.document.report.preview.PreviewView
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.objects.document.report.state.ReportStore
import tech.kzen.auto.client.wrap.material.MaterialLinearProgress
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class ReportController(
    props: RProps
):
    RPureComponent<RProps, ReportController.State>(props),
    ReportStore.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val separatorWidth = 2.px
        val separatorColor = Color("#c3c3c3")
        val selectedColor = Color("#e0e0e0")
    }


    //-----------------------------------------------------------------------------------------------------------------
    class State(
        var reportState: ReportState?
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
//        override fun child(input: RBuilder, handler: RHandler<DocumentControllerProps>): ReactElement {
            return input.child(ReportController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = ReportStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: RProps) {
        reportState = null
    }


    override fun componentDidMount() {
        store.didMount(this)
    }


    override fun componentWillUnmount() {
        store.willUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onReportState(reportState: ReportState?) {
//        console.log("^^^^^ onReportState!!")

        setState {
            this.reportState = reportState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val processState = state.reportState
            ?: return

        renderHeader(processState)

        styledDiv {
            css {
                padding(3.em, 3.em, 7.em, 3.em)
//                padding(0.em, 0.em, 7.em, 0.em)
            }

//            if (processState.nextErrorMessage() != null) {
//                styledDiv {
//                    css {
//                        margin(1.em)
//                        color =  Color.crimson
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Error: ${processState.nextErrorMessage()}"
//                }
//            }

            renderInput(processState)
            renderFormulas(processState)
            renderPreview(processState, false)
            renderFilter(processState)
            renderPreview(processState, true)
            renderAnalysis(processState)
            renderOutput(processState)
        }

        renderRun(processState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader(reportState: ReportState) {
        val isInitiating = reportState.isInitiating()
        val errorMessage = reportState.nextErrorMessage()

        // NB: placing condition here causes some InputBrowser state to be re-initialized?
        // if (! isInitiating && errorMessage == null) {
        //     return
        // }

        StageController.StageContext.Consumer { context ->
            if (isInitiating || errorMessage != null) {
                styledDiv {
                    css {
                        position = Position.fixed
                        top = context.stageTop
                        left = context.stageLeft
                        width = 100.pct.minus(context.stageLeft)
                        zIndex = 99
                    }

                    if (isInitiating) {
                        child(MaterialLinearProgress::class) {}
                    }

                    if (errorMessage != null) {
                        styledDiv {
                            css {
                                backgroundColor = Color.red.lighten(50).withAlpha(0.85)
                                margin(1.em)
                                padding(0.25.em)
                                borderRadius = 3.px
                                fontWeight = FontWeight.bold
                            }
                            +"Error: $errorMessage"
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInput(reportState: ReportState) {
        child(ReportInputView::class) {
            attrs {
                this.reportState = reportState
                dispatcher = store
            }
        }
    }


    private fun RBuilder.renderFormulas(reportState: ReportState) {
        child(ReportFormulaList::class) {
            attrs {
                this.reportState = reportState
                this.dispatcher = store
            }
        }
    }


    private fun RBuilder.renderFilter(reportState: ReportState) {
        child(ReportFilterList::class) {
            attrs {
                this.reportState = reportState
                this.dispatcher = store
            }
        }
    }


    private fun RBuilder.renderPreview(reportState: ReportState, afterFilter: Boolean) {
        child(PreviewView::class) {
            attrs {
                this.reportState = reportState
                this.dispatcher = store
                this.afterFilter = afterFilter
            }
        }
    }


//    private fun RBuilder.renderPivot(reportState: ReportState) {
//        child(ReportPivot::class) {
//            attrs {
//                this.reportState = reportState
//                this.dispatcher = store
//            }
//        }
//    }


    private fun RBuilder.renderAnalysis(reportState: ReportState) {
        child(AnalysisView::class) {
            attrs {
                this.reportState = reportState
                this.dispatcher = store
            }
        }
    }


    private fun RBuilder.renderOutput(reportState: ReportState) {
        child(ReportOutputView2::class) {
            attrs {
                this.reportState = reportState
                this.dispatcher = store
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

            child(ReportRun::class) {
                attrs {
                    this.reportState = reportState
                    dispatcher = store
                }
            }
        }
    }
}