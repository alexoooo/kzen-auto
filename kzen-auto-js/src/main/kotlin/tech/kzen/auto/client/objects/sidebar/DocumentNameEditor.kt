package tech.kzen.auto.client.objects.sidebar

import kotlinx.coroutines.delay
import kotlinx.css.Color
import kotlinx.css.pct
import kotlinx.css.properties.TextDecorationLine
import kotlinx.css.properties.textDecoration
import kotlinx.css.px
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import styled.css
import styled.styledA
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.lib.common.api.model.DocumentName
import tech.kzen.lib.common.api.model.DocumentPath
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

            val nameWithExtension = DocumentName.ofFilenameWithDefaultExtension(state.name)
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
            renderWriter()
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


    private fun RBuilder.renderWriter() {
        child(MaterialTextField::class) {
            attrs {
                style = reactStyle {
                    marginTop = (-6).px
                }

                fullWidth = true
                autoFocus = true

                inputRef = ::onInputRef

                value = state.name
//                        if (NameConventions.isDefault(ObjectName(state.objectName))) {
//                            ""
//                        }
//                        else {
//                            state.objectName
//                        }

                onChange = {
                    val target = it.target as HTMLInputElement
                    onNameChange(target.value)
                }

                onKeyDown = ::handleEnterAndEscape
            }
        }
    }
}