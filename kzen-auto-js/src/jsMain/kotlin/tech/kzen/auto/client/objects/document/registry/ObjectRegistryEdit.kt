package tech.kzen.auto.client.objects.document.registry

import emotion.react.css
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryReflection
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.ClassName
import web.cssom.Display
import web.cssom.VerticalAlign


//---------------------------------------------------------------------------------------------------------------------
external interface ObjectRegistryEditProps: Props {
    var objectLocation: ObjectLocation
    var index: Int
    var className: ClassName
    var reflection: ObjectRegistryReflection?
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
class ObjectRegistryEdit(
    props: ObjectRegistryEditProps
):
    RPureComponent<ObjectRegistryEditProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove() {
        val command = ClassListSpec.removeCommand(props.objectLocation, props.className)
        async {
            props.mirroredGraphStore.apply(command)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.tableRow
            }

            val reflection = props.reflection
            if (reflection != null) {
                +"Source: ${reflection.source}"
                br {}
                +"Error: ${reflection.error}"
                br {}
            }

            +"[${props.className.asString()}]"

            renderDeleteButton()
        }
    }


    private fun ChildrenBuilder.renderDeleteButton() {
        div {
            css {
                display = Display.tableCell
                verticalAlign = VerticalAlign.middle
            }
            IconButton {
                title = "Remove"

                onClick = {
                    onRemove()
                }

                icon("material-symbols:delete") {}
            }
        }
    }
}
