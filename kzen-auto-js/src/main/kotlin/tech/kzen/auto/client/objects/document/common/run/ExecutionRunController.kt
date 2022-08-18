package tech.kzen.auto.client.objects.document.common.run

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle


class ExecutionRunController(
    props: Props
):
    RPureComponent<ExecutionRunController.Props, ExecutionRunController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props : react.Props {
        var thisRunning: Boolean
        var thisSubmitting: Boolean
        var otherRunning: Boolean

        var outputTerminal: Boolean

        var executionRunStore: ExecutionRunStore
        var resetCallback: () -> Unit
//        var reportStore: ReportStore
    }


    interface State : react.State {
//        var fabHover: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        fabHover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onOuterEnter() {
//        setState {
//            fabHover = true
//        }
//    }
//
//
//    private fun onOuterLeave() {
//        setState {
//            fabHover = false
//        }
//    }


    private fun onRunMain() {
        if (props.thisRunning) {
            props.executionRunStore.cancelAsync()
        }
        else if (props.outputTerminal) {
//            props.reportStore.output.resetAsync()
            props.resetCallback()
        }
        else {
            props.executionRunStore.startAndRunAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (props.otherRunning) {
            renderOtherRunning()
        } else {
            renderMainAction()
        }

//        div {
//            attrs {
//                onMouseOverFunction = {
//                    onOuterEnter()
//                }
//                onMouseOutFunction = {
//                    onOuterLeave()
//                }
//            }
//
//            renderInner()
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOtherRunning() {
        child(MaterialFab::class) {
            attrs {
                style = reactStyle {
                    backgroundColor = Color.grey
                    width = 5.em
                    height = 5.em
                }

                title = "Other report is already running, refresh this page after it finishes"
            }

            child(BlockIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 3.em
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderMainAction() {
        val inProgress = props.thisSubmitting

        child(MaterialFab::class) {
            attrs {
                style = reactStyle {
                    backgroundColor =
                        if (props.thisRunning || inProgress) {
                            Color.gold
                        } else {
                            Color.white
                        }

                    width = 5.em
                    height = 5.em
                }

                onClick = {
                    onRunMain()
                }

                title =
                    when {
                        props.thisRunning ->
                            "Cancel"

                        props.outputTerminal ->
                            "Reset"

                        else ->
                            "Run"
                    }
            }

            when {
                props.thisRunning ->
                    renderProgressWithPause(inProgress)

                props.outputTerminal -> {
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