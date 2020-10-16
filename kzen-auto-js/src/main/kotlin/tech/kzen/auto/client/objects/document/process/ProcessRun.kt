package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.objects.document.process.state.*
import tech.kzen.auto.client.wrap.*


// TODO: inline within flow?
class ProcessRun(
    props: Props
):
    RPureComponent<ProcessRun.Props, ProcessRun.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
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


    private fun onRunIndex() {
        props.dispatcher.dispatchAsync(
            ProcessTaskRunRequest(
                ProcessTaskType.Index))
    }


    private fun onRunMain(readyToRun: Boolean, hasSummary: Boolean, inProgress: Boolean) {
        if (! readyToRun || inProgress) {
            return
        }

        when {
            props.processState.isTaskRunning() -> {
                val taskId = props.processState.taskModel?.taskId
                    ?: return

                props.dispatcher.dispatchAsync(
                    ProcessTaskStopRequest(taskId))
            }

            hasSummary -> {
                props.dispatcher.dispatchAsync(
                    ProcessTaskRunRequest(
                        ProcessTaskType.Filter))
            }

            else -> {
                props.dispatcher.dispatchAsync(
                    ProcessTaskRunRequest(
                        ProcessTaskType.Index))
            }
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
            ! props.processState.initiating &&
            ! props.processState.isLoadingError() &&
            (props.processState.columnListing?.isNotEmpty() ?: false)

        val hasSummary =
            props.processState.tableSummary?.columnSummaries?.isNotEmpty() ?: false

        val inProgress =
            props.processState.taskStarting ||
            props.processState.taskStopping

//        console.log("^^^^^^ renderInner | " +
//                "$readyToRun - $hasSummary - $inProgress")

        renderSecondaryActions(readyToRun, hasSummary, inProgress)
        renderMainAction(readyToRun, hasSummary, inProgress)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction(readyToRun: Boolean, hasSummary: Boolean, inProgress: Boolean) {
        child(MaterialFab::class) {
            attrs {
                style = reactStyle {
                    backgroundColor =
                        if (readyToRun) {
                            Color.gold
                        }
                        else {
                            Color.white
                        }

                    width = 5.em
                    height = 5.em
                }

                onClick = {
                    onRunMain(readyToRun, hasSummary, inProgress)
                }

                title =
                    when {
                        props.processState.initiating ->
                            "Loading"

                        props.processState.isLoadingError() || ! readyToRun ->
                            "Please specify valid input"

                        props.processState.indexTaskRunning ->
                            "Pause index"

                        props.processState.filterTaskRunning ->
                            "Stop processing"

                        hasSummary ->
                            "Run"

                        else ->
                            "Index"
                    }
            }

            when {
                props.processState.initiating -> {
                    child(MaterialCircularProgress::class) {}
                }

                props.processState.isLoadingError() -> {
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

                props.processState.isTaskRunning() -> {
                    renderProgressWithPause(inProgress)
                }

                hasSummary -> {
                    child(PlayArrowIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }

                else -> {
                    child(MenuBookIcon::class) {
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
    private fun RBuilder.renderSecondaryActions(readyToRun: Boolean, hasSummary: Boolean, inProgress: Boolean) {
        val showIndex =
            ! props.processState.isTaskRunning() &&
            readyToRun &&
            hasSummary &&
            ! inProgress &&
            (props.processState.taskProgress?.remainingFiles?.isNotEmpty() ?:
                    ! props.processState.indexTaskFinished)

//        console.log("^^^^ renderSecondaryActions: " +
//                "${props.processState.taskProgress?.remainingFiles} - " +
//                "${props.processState.indexTaskFinished}")

        child(MaterialIconButton::class) {
            attrs {
                title = "Index column values"

                style = reactStyle {
                    if (! state.fabHover || ! showIndex) {
                        visibility = Visibility.hidden
                    }
                }

                onClick = {
                    onRunIndex()
                }
            }

            child(MenuBookIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.5.em
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