package tech.kzen.auto.client.objects.document.registry

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.objects.ProjectController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.ClassName
import web.cssom.Display
import web.cssom.NamedColor
import web.cssom.VerticalAlign
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface ObjectRegistryAddProps: Props {
    var objectLocation: ObjectLocation
}


external interface ObjectRegistryAddState: State {
    var newClassName: String
    var adding: Boolean
    var previousError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class ObjectRegistryAdd(
    props: ObjectRegistryAddProps
):
    RPureComponent<ObjectRegistryAddProps, ObjectRegistryAddState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ObjectRegistryAddState.init(props: ObjectRegistryAddProps) {
        newClassName = ""
        adding = false
        previousError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onEdit(value: String) {
        setState {
            newClassName = value
        }
    }


    private fun onKeyDown(event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnter(event) {
            onAdd()
        }
    }


    private fun onAdd() {
        val className = state.newClassName
        if (className.isBlank()) {
            return
        }

        setState {
            adding = true
            previousError = null
        }

        async {
            addSync(className)
        }
    }


    private suspend fun addSync(className: String) {
        val parsedClassName: ClassName
        try {
            parsedClassName = ClassName(className)
        }
        catch (t: Throwable) {
            setState {
                adding = false
                previousError = ExceptionUtils.message(t)
            }
            return
        }

        val command = ClassListSpec.addCommand(props.objectLocation, parsedClassName)

        val result = ClientContext.mirroredGraphStore.apply(
            command, ProjectController.suppressErrorDisplay)

        val error = (result as? MirroredGraphError)
            ?.error
            ?.let { it.message ?: "$result" }

        setState {
            adding = false
            previousError = error

            if (error == null) {
                newClassName = ""
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        ReactHTML.div {
            css {
                display = Display.tableRow
            }

            renderSubmitButton()
            renderClassNameEditor()
        }

        renderErrorIfPresent()
    }


    private fun ChildrenBuilder.renderSubmitButton() {
        div {
            css {
                display = Display.tableCell
                verticalAlign = VerticalAlign.middle
            }
            IconButton {
                title = "Add new object class"
                disabled = state.adding

                onClick = {
                    onAdd()
                }

                AddCircleOutlineIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderClassNameEditor() {
        div {
            css {
                display = Display.tableCell
            }

            TextField {
                size = Size.small
                label = ReactNode("Fully qualified class name")
                value = state.newClassName

                onChange = {
                    val value = (it.target as HTMLInputElement).value
                    onEdit(value)
                }

                onKeyDown = ::onKeyDown
            }
        }
    }


    private fun ChildrenBuilder.renderErrorIfPresent() {
        val previousError = state.previousError
            ?: return

        div {
            css {
                color = NamedColor.red
            }
            +"Error: $previousError"
        }
    }
}