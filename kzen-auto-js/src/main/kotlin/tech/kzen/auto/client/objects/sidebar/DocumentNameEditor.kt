package tech.kzen.auto.client.objects.sidebar

import csstype.*
import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.*
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.SaveIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface DocumentNameEditorProps : PropsWithRef<DocumentNameEditor> {
    var documentPath: DocumentPath

    var initialEditing: Boolean
    var onEditing: (Boolean) -> Unit
}


external interface DocumentNameEditorState: State {
    var editing: Boolean
    var name: String
    var saving: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// TODO: error detection
class DocumentNameEditor(
        props: DocumentNameEditorProps
):
        RPureComponent<DocumentNameEditorProps, DocumentNameEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
//    private var inputRef: HTMLInputElement? = null
    private var inputRef: RefObject<HTMLInputElement> = createRef()



    //-----------------------------------------------------------------------------------------------------------------
    override fun DocumentNameEditorState.init(props: DocumentNameEditorProps) {
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


    // TODO: what does this do and how to make this work (post-migration)
//    private fun onInputRef(inputRef: HTMLInputElement?) {
//        val isNew = this.inputRef == null && inputRef != null
//
//        this.inputRef = inputRef
//
//        if (isNew) {
//            async {
//                delay(1)
//                this.inputRef?.focus()
//            }
//        }
//    }


    private fun handleEnterAndEscape(event: KeyboardEvent<*>) {
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
    override fun ChildrenBuilder.render() {
        if (state.editing) {
            renderEditor()
        }
        else {
            renderReader()
        }
    }


    private fun ChildrenBuilder.renderReader() {
        div {
            css {
                marginTop = 2.px
                width = 100.pct
            }

            +state.name
        }
    }


    private fun ChildrenBuilder.renderEditor() {
        div {
            css {
                display = Display.inlineBlock

                width = 100.pct.minus((4.7).em)
            }

            TextField {
                size = Size.small
                css {
                    marginTop = (-5).px
                }

                fullWidth = true
                autoFocus = true

//                inputRef = { onInputRef() }
                this.inputRef = inputRef

                value = state.name

                onChange = {
                    val target = it.target as HTMLInputElement
                    onNameChange(target.value)
                }

                onKeyDown = ::handleEnterAndEscape
            }
        }

        div {
            css {
//                float = Float.right
                marginTop = (-12).px
            }

            IconButton {
                title = "Cancel name edit (keyboard shortcut: Escape)"

                css {
                    marginLeft = (-3.7).em
                }

                onClick = { onCancel() }

                CancelIcon::class.react {}
            }

            IconButton {
                title = "Save name (keyboard shortcut: Enter)"

                css {
                    marginLeft = (-0.75).em
                    marginRight = (-1).em
                }

                onClick = { onRename() }

                disabled = ! isModified()

                SaveIcon::class.react {}
            }
        }
    }
}