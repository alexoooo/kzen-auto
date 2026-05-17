package tech.kzen.auto.client.objects.document.common.edit

import emotion.react.css
import kotlinx.coroutines.delay
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.SaveIcon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import web.cssom.*
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface ObjectNameEditorProps: Props {
    var objectLocation: ObjectLocation
    var onClose: () -> Unit
}


external interface ObjectNameEditorState: State {
    var objectName: String
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ObjectNameEditor(
    props: ObjectNameEditorProps
):
    RPureComponent<ObjectNameEditorProps, ObjectNameEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private val inputRef = createRef<HTMLInputElement>()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ObjectNameEditorState.init(props: ObjectNameEditorProps) {
        objectName = props.objectLocation.objectPath.name.value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            delay(1)
            inputRef.current?.focus()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isBlank(): Boolean {
        return state.objectName.isBlank()
    }


    private fun isModified(): Boolean {
        return props.objectLocation.objectPath.name.value != state.objectName
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handleEnterAndEscape(event: KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onSave, ::onCancel)
    }


    private fun onNameChange(newValue: String) {
        setState {
            objectName = newValue
        }
    }


    private fun onCancel() {
        props.onClose()
    }


    private fun onSave() {
        if (! isModified()) {
            props.onClose()
            return
        }

        if (isBlank()) {
            return
        }

        val newName = ObjectName(state.objectName)
        async {
            ClientContext.mirroredGraphStore.apply(RenameObjectRefactorCommand(
                props.objectLocation, newName))
        }
        props.onClose()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.inlineBlock
                width = 100.pct.minus(5.em)
            }

            TextField {
                fullWidth = true
                autoFocus = true
                size = Size.small

                this.inputRef = inputRef

                value = state.objectName

                onChange = {
                    val target = it.target as HTMLInputElement
                    onNameChange(target.value)
                }

                onKeyDown = { event ->
                    handleEnterAndEscape(event)
                }
            }
        }

        div {
            css {
                float = Float.right
            }

            IconButton {
                title = "Cancel name edit (keyboard shortcut: Escape)"

                css {
                    marginLeft = (-3).em
                }

                onClick = {
                    onCancel()
                }

                CancelIcon::class.react {}
            }

            IconButton {
                title = "Save name (keyboard shortcut: Enter)"

                css {
                    marginLeft = (-0.5).em
                    marginRight = 0.25.em
                }

                disabled = ! isModified() || isBlank()

                onClick = {
                    onSave()
                }

                SaveIcon::class.react {}
            }
        }
    }
}
