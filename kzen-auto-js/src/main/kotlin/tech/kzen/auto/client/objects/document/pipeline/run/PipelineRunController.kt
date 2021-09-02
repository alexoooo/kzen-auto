package tech.kzen.auto.client.objects.document.pipeline.run

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.attrs
import react.dom.div
import tech.kzen.auto.client.objects.document.pipeline.model.PipelineStore
import tech.kzen.auto.client.objects.document.pipeline.run.model.PipelineRunState
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus


class PipelineRunController(
    props: Props
):
    RPureComponent<PipelineRunController.Props, PipelineRunController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
//        var pipelineState: PipelineState,
        var runState: PipelineRunState,
        var outputStatus: OutputStatus,
        var pipelineStore: PipelineStore
    ): react.Props


    class State(
        var fabHover: Boolean
    ): react.State


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


    private fun onRunMain(running: Boolean, terminal: Boolean) {
        if (running) {
            props.pipelineStore.run.cancelAsync()
        }
        else if (terminal) {
            props.pipelineStore.output.resetAsync()
        }
        else {
            props.pipelineStore.run.startAndRunAsync()
        }

//        val result = ClientContext.restClient.performDetached(
//            store.mainLocation(),
//            PipelineConventions.actionParameter to PipelineConventions.actionDefaultFormat,
//            *paths.map { PipelineConventions.filesParameter to it.asString() }.toTypedArray())


//        if (! readyToRun || inProgress) {
//            return
//        }

//        when {
//            props.reportState.isTaskRunning() -> {
//                val taskId = props.reportState.taskModel?.taskId
//                    ?: return
//
//                props.dispatcher.dispatchAsync(
//                    ReportTaskStopRequest(taskId))
//            }
//
//            status.isTerminal() ->
//                props.dispatcher.dispatchAsync(
//                    ReportResetAction)
//
//            else -> {
//                props.dispatcher.dispatchAsync(
//                    ReportTaskRunRequest(
//                        ReportTaskType.RunReport))
//            }
//        }
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
        val logicStatus = props.runState.logicStatus
            ?: return

//        val readyToRun =
//            ! props.reportState.isInitiating() &&
//            ! props.reportState.isLoadingError() &&
//            (props.reportState.columnListing?.isNotEmpty() ?: false)
//
//        val inProgress =
//            props.reportState.taskStarting ||
//            props.reportState.taskStopping

        renderMainAction(logicStatus)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction(
        logicStatus: LogicStatus
    ) {
        val running = logicStatus.active != null
        val terminal = props.outputStatus.isTerminal()
        val inProgress = props.runState.submitting()

        child(MaterialFab::class) {
            attrs {
                style = reactStyle {
                    backgroundColor =
                        if (running || inProgress) {
                            Color.gold
                        }
                        else {
                            Color.white
                        }

                    width = 5.em
                    height = 5.em
                }

                onClick = {
                    onRunMain(running, terminal)
                }

                title =
                    when {
//                        props.reportState.isInitiating() ->
//                            "Loading"
//
//                        props.reportState.isLoadingError() || ! readyToRun ->
//                            "Please specify valid input"
//
                        running ->
                            "Cancel"

                        terminal ->
                            "Reset"

                        else ->
                            "Run"
                    }
            }

            when {
//                props.reportState.isInitiating() -> {
//                    child(MaterialCircularProgress::class) {}
//                }

//                props.reportState.isLoadingError() -> {
//                    child(ErrorIcon::class) {
//                        attrs {
//                            style = reactStyle {
//                                fontSize = 3.em
//                            }
//                        }
//                    }
//                }

//                ! readyToRun -> {
//                    child(ErrorOutlineIcon::class) {
//                        attrs {
//                            style = reactStyle {
//                                fontSize = 3.em
//                            }
//                        }
//                    }
//                }

                running ->
                    renderProgressWithPause(inProgress)

                terminal -> {
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