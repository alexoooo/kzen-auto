package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.div
import tech.kzen.auto.client.objects.document.process.state.*
import tech.kzen.auto.client.wrap.*


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


    private fun onRunMain(readyToRun: Boolean, hasSummary: Boolean) {
        if (! readyToRun) {
            return
        }

        if (props.processState.isTaskRunning()) {
            // pause
        }
        else if (hasSummary) {
            props.dispatcher.dispatchAsync(
                ProcessTaskRunRequest(
                    ProcessTaskType.Filter))
        }
        else {
            props.dispatcher.dispatchAsync(
                ProcessTaskRunRequest(
                    ProcessTaskType.Index))
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
            ! props.processState.isInitialLoading() &&
                    ! props.processState.isLoadingError() &&
                    (props.processState.columnListing?.isNotEmpty() ?: false)

        val hasSummary =
            props.processState.tableSummary?.columnSummaries?.isNotEmpty() ?: false

        renderSecondaryActions(readyToRun, hasSummary)
        renderMainAction(readyToRun, hasSummary)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction(readyToRun: Boolean, hasSummary: Boolean) {
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
                    onRunMain(readyToRun, hasSummary)
                }

//                if (! readyToRun) {
//                    disabled = true
//                }

                title =
                    when {
                        props.processState.isInitialLoading() ->
                            "Loading"

                        props.processState.isLoadingError() || ! readyToRun ->
                            "Please specify valid input"

                        props.processState.indexTaskRunning ->
                            "Pause index"

                        props.processState.filterTaskRunning ->
                            "Stop filter"

                        hasSummary ->
                            "Filter"

                        else ->
                            "Index"
                    }
            }

            when {
                props.processState.isInitialLoading() -> {
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
                    renderProgressWithPause()
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
    private fun RBuilder.renderSecondaryActions(readyToRun: Boolean, hasSummary: Boolean) {
        val showIndex =
            ! props.processState.isTaskRunning() &&
            readyToRun &&
            hasSummary &&
            ! (props.processState.taskProgress?.remainingFiles?.isEmpty() ?: true)

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
    private fun RBuilder.renderProgressWithPause() {
        child(MaterialCircularProgress::class) {}

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