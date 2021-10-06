package tech.kzen.auto.client.objects.document.report.run

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import react.dom.attrs
import react.dom.div
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.objects.document.report.run.model.ReportRunState
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.paradigm.common.v1.model.LogicStatus


class ReportRunController(
    props: Props
):
    RPureComponent<ReportRunController.Props, ReportRunController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var runState: ReportRunState,
        var outputStatus: OutputStatus,
        var reportStore: ReportStore
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
            props.reportStore.run.cancelAsync()
        }
        else if (terminal) {
            props.reportStore.output.resetAsync()
        }
        else {
            props.reportStore.run.startAndRunAsync()
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
        val logicStatus = props.runState.logicStatus
            ?: return

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
                        running ->
                            "Cancel"

                        terminal ->
                            "Reset"

                        else ->
                            "Run"
                    }
            }

            when {
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