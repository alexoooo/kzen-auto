package tech.kzen.auto.client.objects.document.script.progress

import js.objects.unsafeJso
import mui.material.Fab
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import web.cssom.NamedColor
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptProgressControllerProps: Props {
    var active: Boolean
    var hasProgress: Boolean
    var scriptProgressStore: ScriptProgressStore
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptProgressController(
    props: ScriptProgressControllerProps
):
    RPureComponent<ScriptProgressControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onRunMain() {
        async {
            props.scriptProgressStore.clear()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        //+"${props.active} - ${props.hasProgress}"

        if (props.active || !props.hasProgress) {
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

            icon("material-symbols:replay") {
                style = unsafeJso {
                    fontSize = 3.em
                }
            }
        }
    }
}