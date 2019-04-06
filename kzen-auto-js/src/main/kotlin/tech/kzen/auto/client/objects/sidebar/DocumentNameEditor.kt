package tech.kzen.auto.client.objects.sidebar

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.css.properties.TextDecorationLine
import kotlinx.css.properties.textDecoration
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import styled.css
import styled.styledA
import styled.styledDiv
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.structure.notation.edit.RenameDocumentRefactorCommand


class DocumentNameEditor(
        props: Props
):
        RComponent<DocumentNameEditor.Props, DocumentNameEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath
    ): RProps


    class State(
            var editing: Boolean,
            var name: String,
            var saving: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var inputRef: HTMLInputElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun DocumentNameEditor.State.init(props: DocumentNameEditor.Props) {
//        console.log("ObjectNameEditor | State.init - ${props.objectName}", Date.now())
        name = displayPath()

        editing = false
        saving = false
//        readerHover = false
    }


    override fun componentDidUpdate(
            prevProps: DocumentNameEditor.Props,
            prevState: DocumentNameEditor.State,
            snapshot: Any
    ) {
        if (state.saving && ! prevState.saving) {
            saveAsync()
        }
    }


    private fun saveAsync() {
        async {
//            // NB: not sure why this is necessary, without it state.saving doesn't show
//            delay(1)

            val nameWithExtension = DocumentName.ofYaml(state.name)
            ClientContext.commandBus.apply(RenameDocumentRefactorCommand(
                    props.documentPath, nameWithExtension))

//            // NB: no need to set saving = false, the component will un-mount?
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun onEdit() {
        setState {
            editing = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun displayPath(): String {
        return props.documentPath.name!!.value.substringBeforeLast(".")
    }


    private fun onInputRef(inputRef: HTMLInputElement?) {
        val isNew = this.inputRef == null && inputRef != null

        this.inputRef = inputRef

        if (isNew) {
            async {
                delay(1)
                this.inputRef?.focus()
            }
        }
    }

    private fun handleEnterAndEscape(event: KeyboardEvent) {
//        console.log("event.key: ${event.key}", event)

        when {
            event.key == "Enter" -> onRename()
            event.key == "Escape" -> onCancel()
            else -> return
        }

        event.preventDefault()
    }


    private fun onCancel() {
        setState {
            editing = false
        }
    }


    private fun onRename() {
        if (! isModified()) {
            onCancel()
            return
        }

        setState {
            editing = false
            saving = true
        }
    }


    private fun isModified(): Boolean {
        return displayPath() != state.name
    }


    private fun onNameChange(newValue: String) {
        setState {
            name = newValue
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (state.editing) {
            renderEditor()
        }
        else {
            styledA {
                css {
                    width = 100.pct

                    color = Color.inherit
                    textDecoration(TextDecorationLine.initial)
                }

                attrs {
                    href = "#" + props.documentPath.asString()
                }

                +state.name
            }
        }
    }


    private fun RBuilder.renderEditor() {
        styledDiv {
            css {
                display = Display.inlineBlock

                width = 100.pct.minus(4.5.em)
//                height = ActionController.headerHeight

//                marginTop = 10.px
//                marginTop = (-14).px
                marginTop = (-20).px
            }

            child(MaterialTextField::class) {
                attrs {
//                    style = reactStyle {
//                        marginTop = (-6).px
//                    }

                    fullWidth = true
                    autoFocus = true

                    inputRef = ::onInputRef

                    value = state.name

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
                float = Float.right
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Cancel name edit (keyboard shortcut: Escape)"

                    style = reactStyle {
                        marginLeft = (-3.5).em
                    }

                    onClick = ::onCancel
                }

                child(CancelIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Save name (keyboard shortcut: Enter)"

                    style = reactStyle {
                        //                        marginLeft = (-0.5).em
                        marginLeft = (-0.75).em
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