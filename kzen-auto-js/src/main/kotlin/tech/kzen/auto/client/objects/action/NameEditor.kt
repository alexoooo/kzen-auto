package tech.kzen.auto.client.objects.action

import kotlinx.coroutines.experimental.delay
import kotlinx.css.*
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.edit.RenameObjectCommand
import tech.kzen.lib.common.notation.model.ProjectNotation


@Suppress("unused")
class NameEditor(
        props: NameEditor.Props
) :
        RComponent<NameEditor.Props, NameEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectName: String,
            var notation: ProjectNotation
    ) : RProps


    class State(
            var editing: Boolean,
            var objectName: String,
            var saving: Boolean
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun NameEditor.State.init(props: NameEditor.Props) {
//        console.log("NameEditor | State.init - ${props.objectName}", Date.now())
        objectName = props.objectName

        editing = false
        saving = false
    }



    override fun componentDidUpdate(prevProps: Props, prevState: State, snapshot: Any) {
        if (state.saving && ! prevState.saving) {
            saveAsync()
        }
    }


    private fun saveAsync() {
         async {
             // NB: not sure why this is necessary, without it state.saving doesn't show
             delay(1)

             ClientContext.commandBus.apply(RenameObjectCommand(
                    props.objectName, state.objectName))

             // NB: no need to set saving = false, the component will un-mount
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handleEnterAndEscape(event: KeyboardEvent) {
//        console.log("event.key: ${event.key}", event)

        when {
            event.key == "Enter" -> onRename()
            event.key == "Escape" -> onCancel()
            else -> return
        }

        event.preventDefault()
    }


    private fun onNameChange(newValue: String) {
        setState {
            objectName = newValue
        }
    }


    private fun onCancel() {
        setState {
            editing = false
        }
    }


    private fun onRename() {
        if (! isModified()) {
            return
        }

        setState {
            editing = false
            saving = true
        }
    }


    private fun onEdit() {
        setState {
            editing = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isModified(): Boolean {
        return props.objectName != state.objectName
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                height = ActionController.headerHeight
                width = 100.pct
            }

            if (state.editing) {
                renderEditor()
            }
            else {
                renderReader()
            }
        }
    }


    private fun RBuilder.renderReader() {
        styledDiv {
            css {
                cursor = Cursor.pointer
                height = ActionController.headerHeight
                width = 100.pct
            }

            attrs {
                onClickFunction = {
                    onEdit()
                }
            }

            styledSpan {
                css {
                    width = 100.pct
                    height = ActionController.headerHeight

                    fontSize = LinearDimension("1.5em")
                    fontWeight = FontWeight.bold
                }

                attrs {
                    title = "Edit name"
                }

                if (state.saving) {
                    +state.objectName
                }
                else {
                    +props.objectName
                }
            }
        }
    }


    private fun RBuilder.renderEditor() {
        styledDiv {
            css {
                display = Display.inlineBlock

                width = 100.pct.minus(4.em)
                height = ActionController.headerHeight
            }

            child(MaterialTextField::class) {
                attrs {
                    label = "Name"
                    fullWidth = true
                    autoFocus = true

                    value = state.objectName

                    onChange = {
                        val target = it.target as HTMLInputElement
                        onNameChange(target.value)
                    }

                    onKeyDown = ::handleEnterAndEscape
                }
            }
        }

        styledDiv {
            css {
                float = kotlinx.css.Float.right
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        marginLeft = (-3).em
                    }

                    onClick = ::onCancel
                }

                child(CancelIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        marginLeft = (-0.5).em
                        marginRight = (-1).em
                    }

                    onClick = ::onRename

                    disabled = ! isModified()
                }

                child(SaveIcon::class) {}
            }
        }
    }
}