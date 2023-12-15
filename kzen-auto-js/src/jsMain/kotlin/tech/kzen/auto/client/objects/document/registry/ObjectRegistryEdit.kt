package tech.kzen.auto.client.objects.document.registry

import emotion.react.css
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.ProjectController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryReflection
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.platform.ClassName
import web.cssom.Display
import web.cssom.VerticalAlign


//---------------------------------------------------------------------------------------------------------------------
external interface ObjectRegistryEditProps: Props {
    var objectLocation: ObjectLocation
    var index: Int
    var className: ClassName
    var reflection: ObjectRegistryReflection?
}


external interface ObjectRegistryEditState: State {
//    var newClassName: String
    var editing: Boolean
    var previousError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class ObjectRegistryEdit(
    props: ObjectRegistryEditProps
):
    RPureComponent<ObjectRegistryEditProps, ObjectRegistryEditState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ObjectRegistryEditState.init(props: ObjectRegistryEditProps) {
//        newClassName = ""
        editing = false
        previousError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onEdit(value: String) {
//        setState {
//            newClassName = value
//        }
//    }
//
//
//    private fun onKeyDown(event: react.dom.events.KeyboardEvent<*>) {
//        ClientInputUtils.handleEnter(event) {
//            onAdd()
//        }
//    }


    private fun onRemove() {
        setState {
            editing = true
            previousError = null
        }

        async {
            removeSync()
        }
    }


    private suspend fun removeSync() {
        val command = ClassListSpec.removeCommand(props.objectLocation, props.className)

        val result = ClientContext.mirroredGraphStore.apply(
            command, ProjectController.suppressErrorDisplay)

        val error = (result as? MirroredGraphError)
            ?.error
            ?.let { it.message ?: "$result" }

        setState {
            editing = false
            previousError = error
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
//            renderClassNameEditor()
        }

//        renderErrorIfPresent()
    }


    private fun ChildrenBuilder.renderDeleteButton() {
        div {
            css {
                display = Display.tableCell
                verticalAlign = VerticalAlign.middle
            }
            IconButton {
                title = "Remove"
//                disabled = state.adding

                onClick = {
                    onRemove()
                }

                DeleteIcon::class.react {}
            }
        }
    }

//
//    private fun ChildrenBuilder.renderClassNameEditor() {
//        div {
//            css {
//                display = Display.tableCell
//            }
//
//            TextField {
//                size = Size.small
//                label = ReactNode("Fully qualified class name")
//                value = state.newClassName
//
//                onChange = {
//                    val value = (it.target as HTMLInputElement).value
//                    onEdit(value)
//                }
//
//                onKeyDown = ::onKeyDown
//            }
//        }
//    }
//
//
//    private fun ChildrenBuilder.renderErrorIfPresent() {
//        val previousError = state.previousError
//            ?: return
//
//        div {
//            css {
//                color = NamedColor.red
//            }
//            +"Error: $previousError"
//        }
//    }
}