package tech.kzen.auto.client.objects.document.report.run

import emotion.react.css
import js.objects.jso
import mui.material.Fab
import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.ReplayIcon
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ReportRunControllerProps: Props {
    var thisRunning: Boolean
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
    private fun onRunMain() {
        props.reportStore.output.resetAsync()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        renderMainAction()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderMainAction() {
        if (props.thisRunning || ! props.outputTerminal) {
            return
        }

        Fab {
            css {
                backgroundColor = NamedColor.white
                width = 5.em
                height = 5.em
            }

            onClick = {
                onRunMain()
            }

            title = "Reset"

            ReplayIcon::class.react {
                style = jso {
                    fontSize = 3.em
                }
            }
        }
    }
}