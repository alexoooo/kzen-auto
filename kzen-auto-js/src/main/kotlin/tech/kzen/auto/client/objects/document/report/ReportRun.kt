package tech.kzen.auto.client.objects.document.report

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.wrap.*


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


//    private fun onRunIndex() {
//        props.dispatcher.dispatchAsync(
//            ProcessTaskRunRequest(
//                ReportTaskType.Index))
//    }


    private fun onRunMain(readyToRun: Boolean, /*hasSummary: Boolean, */inProgress: Boolean) {
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

//            hasSummary -> {
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
            ! props.reportState.initiating &&
            ! props.reportState.isLoadingError() &&
            (props.reportState.columnListing?.isNotEmpty() ?: false)

//        val hasSummary =
//            props.reportState.tableSummary?.columnSummaries?.isNotEmpty() ?: false

        val inProgress =
            props.reportState.taskStarting ||
            props.reportState.taskStopping

//        console.log("^^^^^^ renderInner | " +
//                "$readyToRun - $hasSummary - $inProgress")

//        renderSecondaryActions(readyToRun, hasSummary, inProgress)
        renderMainAction(readyToRun, /*hasSummary,*/ inProgress)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction(readyToRun: Boolean, /*hasSummary: Boolean,*/ inProgress: Boolean) {
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
                    onRunMain(readyToRun/*, hasSummary*/, inProgress)
                }

                title =
                    when {
                        props.reportState.initiating ->
                            "Loading"

                        props.reportState.isLoadingError() || ! readyToRun ->
                            "Please specify valid input"

//                        props.reportState.indexTaskRunning ->
//                            "Pause index"

                        props.reportState.filterTaskRunning ->
                            "Cancel"

//                        hasSummary ->
                        else ->
                            "Run"

//                        else ->
//                            "Index"
                    }
            }

            when {
                props.reportState.initiating -> {
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

//                hasSummary -> {
                else -> {
                    child(PlayArrowIcon::class) {
                        attrs {
                            style = reactStyle {
                                fontSize = 3.em
                            }
                        }
                    }
                }
//
//                else -> {
//                    child(MenuBookIcon::class) {
//                        attrs {
//                            style = reactStyle {
//                                fontSize = 3.em
//                            }
//                        }
//                    }
//                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderSecondaryActions(readyToRun: Boolean, hasSummary: Boolean, inProgress: Boolean) {
//        val showIndex =
//            ! props.reportState.isTaskRunning() &&
//            readyToRun &&
//            hasSummary &&
//            ! inProgress &&
//            (props.reportState.taskProgress?.remainingFiles?.isNotEmpty() ?:
//                    true /*! props.reportState.indexTaskFinished*/)
//
////        console.log("^^^^ renderSecondaryActions: " +
////                "${props.processState.taskProgress?.remainingFiles} - " +
////                "${props.processState.indexTaskFinished}")
//
//        child(MaterialIconButton::class) {
//            attrs {
//                title = "Index column values"
//
//                style = reactStyle {
//                    if (! state.fabHover || ! showIndex) {
//                        visibility = Visibility.hidden
//                    }
//                }
//
//                onClick = {
////                    onRunIndex()
//                }
//            }
//
//            child(MenuBookIcon::class) {
//                attrs {
//                    style = reactStyle {
//                        fontSize = 1.5.em
//                    }
//                }
//            }
//        }
//    }


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