package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.report.output.OutputStatus


class ReportRun(
    props: Props
):
    RPureComponent<ReportRun.Props, ReportRun.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


    class State(
        var fabHover: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        fabHover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onOuterEnter() {
        setState {
            fabHover = true
        }
    }


    private fun onOuterLeave() {
        setState {
            fabHover = false
        }
    }


    private fun onRunMain(readyToRun: Boolean, status: OutputStatus, inProgress: Boolean) {
        if (! readyToRun || inProgress) {
            return
        }

        when {
            props.reportState.isTaskRunning() -> {
                val taskId = props.reportState.taskModel?.taskId
                    ?: return

                props.dispatcher.dispatchAsync(
                    ReportTaskStopRequest(taskId))
            }

            status.isTerminal() ->
                props.dispatcher.dispatchAsync(
                    ReportResetAction)

            else -> {
                props.dispatcher.dispatchAsync(
                    ReportTaskRunRequest(
                        ReportTaskType.RunReport))
            }

//            else -> {
//                props.dispatcher.dispatchAsync(
//                    ProcessTaskRunRequest(
//                        ReportTaskType.Index))
//            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        div {
            attrs {
                onMouseOverFunction = {
                    onOuterEnter()
                }
                onMouseOutFunction = {
                    onOuterLeave()
                }
            }

            renderInner()
        }
    }


    private fun RBuilder.renderInner() {
        val readyToRun =
            ! props.reportState.isInitiating() &&
            ! props.reportState.isLoadingError() &&
            (props.reportState.columnListing?.isNotEmpty() ?: false)

        val inProgress =
            props.reportState.taskStarting ||
            props.reportState.taskStopping

        val status =
            props.reportState.outputInfo?.status ?: OutputStatus.Missing

//        +"status: $status"

        renderMainAction(readyToRun, status, inProgress)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction(readyToRun: Boolean, status: OutputStatus, inProgress: Boolean) {
        child(MaterialFab::class) {
            attrs {
                style = reactStyle {
                    backgroundColor =
                        if (readyToRun && ! status.isTerminal()) {
                            Color.gold
                        }
                        else {
                            Color.white
                        }

                    width = 5.em
                    height = 5.em
                }

                onClick = {
                    onRunMain(readyToRun, status, inProgress)
                }

                title =
                    when {
                        props.reportState.isInitiating() ->
                            "Loading"

                        props.reportState.isLoadingError() || ! readyToRun ->
                            "Please specify valid input"

                        props.reportState.taskRunning ->
                            "Cancel"

                        status.isTerminal() ->
                            "Reset"

                        else ->
//                            "Play"
                            "Run"
                    }
            }

            when {
                props.reportState.isInitiating() -> {
                    child(MaterialCircularProgress::class) {}
                }

                props.reportState.isLoadingError() -> {
                    child(ErrorIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }

                ! readyToRun -> {
                    child(ErrorOutlineIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }

                props.reportState.isTaskRunning() -> {
                    renderProgressWithPause(inProgress)
                }

                status.isTerminal() -> {
                    child(ReplayIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }

                else -> {
                    child(PlayArrowIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderProgressWithPause(inProgress: Boolean) {
        child(MaterialCircularProgress::class) {}

        if (inProgress) {
            return
        }

        child(PauseIcon::class) {
            attrs {
                style = reactStyle {
                    fontSize = 3.em
                    margin = "auto"
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    bottom = 0.px
                    right = 0.px
                }
            }
        }
    }
}