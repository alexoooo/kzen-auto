package tech.kzen.auto.client.objects.document.report.run

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.CircularProgress
import mui.material.Fab
import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.BlockIcon
import tech.kzen.auto.client.wrap.material.PauseIcon
import tech.kzen.auto.client.wrap.material.PlayArrowIcon
import tech.kzen.auto.client.wrap.material.ReplayIcon


//---------------------------------------------------------------------------------------------------------------------
external interface ReportRunControllerProps: Props {
    var thisRunning: Boolean
    var thisSubmitting: Boolean
    var otherRunning: Boolean

    var outputTerminal: Boolean

    var reportStore: ReportStore
}


//---------------------------------------------------------------------------------------------------------------------
class ReportRunController(
    props: ReportRunControllerProps
):
    RPureComponent<ReportRunControllerProps, State>(props)
{
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
            props.reportStore.run.cancelAsync()
        }
        else if (props.outputTerminal) {
            props.reportStore.output.resetAsync()
        }
        else {
            props.reportStore.run.startAndRunAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (props.otherRunning) {
            renderOtherRunning()
        }
        else {
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
    private fun ChildrenBuilder.renderOtherRunning() {
        Fab {
            css {
                backgroundColor = NamedColor.grey
                width = 5.em
                height = 5.em
            }

            title = "Other report is already running, refresh this page after it finishes"

            BlockIcon::class.react {
                style = jso {
                    fontSize = 3.em
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderMainAction() {
        val inProgress = props.thisSubmitting

        Fab {
            css {
                backgroundColor =
                    if (props.thisRunning || inProgress) {
                        NamedColor.gold
                    }
                    else {
                        NamedColor.white
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

            when {
                props.thisRunning ->
                    renderProgressWithPause(inProgress)

                props.outputTerminal -> {
                    ReplayIcon::class.react {
                        style = jso {
                            fontSize = 3.em
                        }
                    }
                }

                else -> {
                    PlayArrowIcon::class.react {
                        style = jso {
                            fontSize = 3.em
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderProgressWithPause(inProgress: Boolean) {
        CircularProgress {}

        if (inProgress) {
            return
        }

        PauseIcon::class.react {
            style = jso {
                fontSize = 3.em
                margin = Auto.auto
                position = Position.absolute
                top = 0.px
                left = 0.px
                bottom = 0.px
                right = 0.px
            }
        }
    }
}