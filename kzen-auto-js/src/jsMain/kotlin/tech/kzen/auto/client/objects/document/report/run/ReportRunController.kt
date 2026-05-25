package tech.kzen.auto.client.objects.document.report.run

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Fab
import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.report.model.ReportStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
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
        if (props.thisRunning || !props.outputTerminal) {
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

            iconByName("Replay") {
                style = unsafeJso {
                    fontSize = 3.em
                }
            }
        }
    }
}