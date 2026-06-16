package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Popover
import mui.material.PopoverOrigin
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.PropsWithRef
import react.RefObject
import react.State
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameFolderRefactorCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*
import web.html.HTMLDivElement
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface DocumentNameEditorProps : PropsWithRef<DocumentNameEditor> {
    var documentPath: DocumentPath
    var mirroredGraphStore: MirroredGraphStore
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
    companion object {
        // Left inset (px) of the TextField's text within the card — card padding + input border + input
        // padding. The popover is shifted left by this much so the editable text overlays the plain name.
        private const val textFieldTextInset = 18
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private var inputRef: HTMLInputElement? = null
    private var inputRef: RefObject<HTMLInputElement> = createRef()

    // The in-row name text doubles as the popover anchor, so the floating edit card lifts out of
    // exactly the row it replaces (and overhangs right, escaping the sidebar's overflow clip).
    private var readerRef: RefObject<HTMLDivElement> = createRef()



    //-----------------------------------------------------------------------------------------------------------------
    override fun DocumentNameEditorState.init(props: DocumentNameEditorProps) {
//        console.log("ObjectNameEditor | State.init - ${props.objectName}", Date.now())
        name = displayPath()

        editing = false
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
    }


    private fun onRename() {
        if (!isModified()) {
            onCancel()
            return
        }

        val newName = DocumentName(state.name)

        setState {
            editing = false
            saving = true
        }

        // a folder and a document rename through the same editor — the path form selects the refactor
        val command =
            if (props.documentPath.folder) {
                RenameFolderRefactorCommand(props.documentPath, newName)
            }
            else {
                RenameDocumentRefactorCommand(props.documentPath, newName)
            }

        async {
            props.mirroredGraphStore.apply(command)

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
        renderReader()

        if (state.editing) {
            renderEditorPopover()
        }
    }


    private fun ChildrenBuilder.renderReader() {
        div {
            css {
                marginTop = 2.px
                width = 100.pct
            }

            ref = readerRef

            +state.name
        }
    }


    // The editor floats in a portal anchored to the reader row. A portal is required (not just nicer):
    // the sidebar column has overflow:auto, so an in-flow card that overhangs its right edge would be
    // clipped. Vertical centre on both the anchor and the card lines the centred TextField up with the
    // plain name (self-adjusting to padding). Horizontally the anchor is the row's left, but the card's
    // origin is inset by the TextField's text decoration (border + padding) so the editable text — not
    // the card edge — overlays the plain name it pops over.
    private fun ChildrenBuilder.renderEditorPopover() {
        val anchorPoint: PopoverOrigin = unsafeJso {
            asDynamic().vertical = "center"
            asDynamic().horizontal = "left"
        }
        val cardPoint: PopoverOrigin = unsafeJso {
            asDynamic().vertical = "center"
            asDynamic().horizontal = textFieldTextInset
        }

        Popover {
            open = state.editing
            onClose = { _, _ -> onCancel() }
            anchorEl = readerRef.current
            anchorOrigin = anchorPoint
            transformOrigin = cardPoint

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center

                    width = 22.em
                    padding = 4.px
                }

                div {
                    css {
                        flexGrow = number(1.0)
                    }

                    TextField {
                        size = Size.small

                        fullWidth = true
                        autoFocus = true

                        this.inputRef = inputRef

                        value = state.name

                        onChange = {
                            val target = it.target as HTMLInputElement
                            onNameChange(target.value)
                        }

                        onKeyDown = ::handleEnterAndEscape
                    }
                }

                IconButton {
                    title = "Cancel name edit (keyboard shortcut: Escape)"

                    onClick = { onCancel() }

                    icon("material-symbols:cancel") {}
                }

                IconButton {
                    title = "Save name (keyboard shortcut: Enter)"

                    onClick = { onRename() }

                    disabled = !isModified()

                    icon("material-symbols:save") {}
                }
            }
        }
    }
}