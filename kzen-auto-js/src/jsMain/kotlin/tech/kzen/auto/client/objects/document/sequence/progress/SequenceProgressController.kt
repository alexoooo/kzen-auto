package tech.kzen.auto.client.objects.document.sequence.progress

import js.objects.jso
import mui.material.Fab
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.ReplayIcon
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface SequenceProgressControllerProps: Props {
    var active: Boolean
    var hasProgress: Boolean
    var sequenceProgressStore: SequenceProgressStore
}


//---------------------------------------------------------------------------------------------------------------------
class SequenceProgressController(
    props: SequenceProgressControllerProps
):
    RPureComponent<SequenceProgressControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onRunMain() {
        async {
            props.sequenceProgressStore.clear()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        //+"${props.active} - ${props.hasProgress}"

        if (props.active || ! props.hasProgress) {
            return
        }

        Fab {
            sx {
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