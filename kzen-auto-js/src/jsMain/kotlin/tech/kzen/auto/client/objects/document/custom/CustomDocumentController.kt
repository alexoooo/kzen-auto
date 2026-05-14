package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.edit.YamlEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomDocumentControllerState: State {
    var clientState: ClientState?
    var loadedFor: DocumentPath?
    var editorValue: String
    var serverNotation: DocumentObjectNotation?
    var saving: Boolean
    var lastError: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomDocumentController(
    props: Props
):
    RPureComponent<Props, CustomDocumentControllerState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    CustomDocumentController::class.react {
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomDocumentControllerState.init(props: Props) {
        clientState = null
        loadedFor = null
        editorValue = ""
        serverNotation = null
        saving = false
        lastError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }
        syncFromClientState(clientState)
    }


    private fun syncFromClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState
            .graphStructure()
            .graphNotation
            .documents[documentPath]
            ?: return

        if (!CustomConventions.isCustomDocument(documentNotation)) {
            return
        }

        val newServerNotation = documentNotation.objects

        if (state.loadedFor != documentPath || state.serverNotation == null) {
            setState {
                loadedFor = documentPath
                editorValue = ClientContext.notationParser.unparseDocument(newServerNotation, "")
                serverNotation = newServerNotation
                lastError = null
            }
            return
        }

        if (newServerNotation == state.serverNotation) {
            return
        }

        if (!isEditorModified()) {
            setState {
                editorValue = ClientContext.notationParser.unparseDocument(newServerNotation, "")
                serverNotation = newServerNotation
            }
        }
        else {
            setState {
                serverNotation = newServerNotation
            }
        }
    }


    private fun isEditorModified(): Boolean {
        val serverNotation = state.serverNotation
            ?: return false

        return try {
            val parsed = ClientContext.notationParser.parseDocumentObjects(state.editorValue)
            parsed != serverNotation
        }
        catch (e: Throwable) {
            true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onEditorChange(newValue: String) {
        setState {
            editorValue = newValue
        }
    }


    private fun onSave() {
        val documentPath = state.loadedFor
            ?: return

        if (state.saving) {
            return
        }

        if (! isEditorModified()) {
            return
        }

        val payload = state.editorValue

        setState {
            saving = true
            lastError = null
        }

        async {
            val parsed = try {
                ClientContext.notationParser.parseDocumentObjects(payload)
            }
            catch (e: Throwable) {
                setState {
                    saving = false
                    lastError = e.message ?: e.toString()
                }
                return@async
            }

            val command = SetDocumentObjectsCommand(documentPath, parsed)
            val result = ClientContext.mirroredGraphStore.apply(command)

            when (result) {
                is MirroredGraphSuccess -> {
                    setState {
                        serverNotation = parsed
                        saving = false
                    }
                }

                is MirroredGraphError -> {
                    setState {
                        saving = false
                        lastError = result.error.message ?: result.error.toString()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState
            .graphStructure()
            .graphNotation
            .documents[documentPath]
            ?: return

        if (!CustomConventions.isCustomDocument(documentNotation)) {
            return
        }

        val modified = isEditorModified()
        val saveDisabled = !modified || state.saving

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                Button {
                    variant = ButtonVariant.contained
                    size = Size.small
                    disabled = saveDisabled
                    onClick = { onSave() }
                    +(if (state.saving) "Saving..." else "Save")
                }

                if (modified && !state.saving) {
                    span {
                        css {
                            marginLeft = 1.em
                            fontStyle = FontStyle.italic
                            color = Color("rgb(128, 80, 0)")
                        }
                        +"unsaved changes"
                    }
                }
            }

            YamlEditor::class.react {
                value = state.editorValue
                onChange = ::onEditorChange
                onSave = ::onSave
                error = state.lastError
                disabled = state.saving
            }
        }
    }
}
