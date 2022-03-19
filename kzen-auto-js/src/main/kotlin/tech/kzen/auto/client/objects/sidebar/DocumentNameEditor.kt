package tech.kzen.auto.client.objects.sidebar

import kotlinx.coroutines.delay
import kotlinx.css.*
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.material.SaveIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand


// TODO: error detection
class DocumentNameEditor(
        props: Props
):
        RPureComponent<DocumentNameEditor.Props, DocumentNameEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var documentPath: DocumentPath,

            var initialEditing: Boolean,
            var onEditing: (Boolean) -> Unit
    ): react.Props


    class State(
            var editing: Boolean,
            var name: String,
            var saving: Boolean
    ): react.State


    //-----------------------------------------------------------------------------------------------------------------
    private var inputRef: HTMLInputElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ObjectNameEditor | State.init - ${props.objectName}", Date.now())
        name = displayPath()

        editing = props.initialEditing
        saving = false
//        readerHover = false
    }


//    override fun componentDidUpdate(
//            prevProps: Props,
//            prevState: State,
//            snapshot: Any
//    ) {
//        if (state.saving && ! prevState.saving) {
//            saveAsync()
//        }
//    }
//
//
//    private fun saveAsync() {
//        async {
//            val nameWithExtension = DocumentName.ofYaml(state.name)
//            ClientContext.commandBus.apply(RenameDocumentRefactorCommand(
//                    props.documentPath, nameWithExtension))
//
////            // NB: no need to set saving = false, the component will un-mount?
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun onEdit() {
        setState {
            editing = true
        }
        props.onEditing(true)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun displayPath(): String {
        return props.documentPath.name.value
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
        ClientInputUtils.handleEnterAndEscape(
            event, ::onRename, ::onCancel)
    }


    private fun onCancel() {
        setState {
            editing = false
            name = displayPath()
        }
        props.onEditing(false)
    }


    private fun onRename() {
        if (! isModified()) {
            onCancel()
            return
        }

        val nameWithExtension = DocumentName(state.name)

        setState {
            editing = false
            saving = true
        }
        props.onEditing(false)

        async {
            ClientContext.mirroredGraphStore.apply(RenameDocumentRefactorCommand(
                    props.documentPath, nameWithExtension))

            // NB: no need to set saving = false, the component will un-mount
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
            renderReader()
        }
    }


    private fun RBuilder.renderReader() {
        styledDiv {
            css {
                marginTop = 2.px
                width = 100.pct
            }

            +state.name
        }
    }


    private fun RBuilder.renderEditor() {
        styledDiv {
            css {
                display = Display.inlineBlock

                width = 100.pct.minus(4.7.em)
            }

            child(MaterialTextField::class) {
                attrs {
                    size = "small"
                    style = reactStyle {
                        marginTop = (-5).px
                    }

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
                marginTop = (-12).px
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Cancel name edit (keyboard shortcut: Escape)"

                    style = reactStyle {
                        marginLeft = (-3.7).em
                    }

                    onClick = ::onCancel
                }

                child(CancelIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Save name (keyboard shortcut: Enter)"

                    style = reactStyle {
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