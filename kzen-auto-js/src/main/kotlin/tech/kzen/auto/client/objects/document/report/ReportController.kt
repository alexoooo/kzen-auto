package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.document.report.filter.ReportFilterList
import tech.kzen.auto.client.objects.document.report.pivot.ReportPivot
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.objects.document.report.state.ReportStore
import tech.kzen.auto.client.wrap.MaterialLinearProgress
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
//        console.log("^^^^^ onProcessState!!")

        setState {
            this.reportState = reportState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val processState = state.reportState
            ?: return

        renderInitialLoading(processState)

        styledDiv {
            css {
                padding(3.em, 3.em, 7.em, 3.em)
            }

            if (processState.nextErrorMessage() != null) {
                styledDiv {
                    css {
                        margin(1.em)
                        color =  Color.crimson
                        fontWeight = FontWeight.bold
                    }
                    +"Error: ${processState.nextErrorMessage()}"
                }
            }

            renderInput(processState)
            renderFilter(processState)
            renderPivot(processState)
            renderOutput(processState)
        }

        renderRun(processState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInitialLoading(reportState: ReportState) {
        if (! reportState.initiating) {
            return
        }

        StageController.StageContext.Consumer { context ->
            styledDiv {
                css {
                    position = Position.fixed
                    top = context.stageTop
                    left = context.stageLeft
                    width = 100.pct
                    zIndex = 99
                }

                child(MaterialLinearProgress::class) {}
            }
        }
    }


    private fun RBuilder.renderInput(reportState: ReportState) {
        child(ReportInputView::class) {
            attrs {
                this.reportState = reportState
                dispatcher = store
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


    private fun RBuilder.renderPivot(reportState: ReportState) {
        child(ReportPivot::class) {
            attrs {
                this.reportState = reportState
                this.dispatcher = store
            }
        }
    }


    private fun RBuilder.renderOutput(reportState: ReportState) {
        child(ReportOutputView::class) {
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