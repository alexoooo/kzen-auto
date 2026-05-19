package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
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
import web.cssom.Margin
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomControllerProps: Props {
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomControllerState: State {
    var clientState: ClientState?
    var loadedFor: DocumentPath?
    var editorValue: String
    var serverNotation: DocumentObjectNotation?
    var saving: Boolean
    var lastError: String?
    var viewMode: CustomViewMode
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomController(
    props: CustomControllerProps
):
    RPureComponent<CustomControllerProps, CustomControllerState>(props),
    ClientStateGlobal.Observer,
    CustomGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    CustomHeader::class.react {
                        block()
                    }
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    CustomController::class.react {
                        this.attributeEditorManager = this@Wrapper.attributeEditorManager
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomControllerState.init(props: CustomControllerProps) {
        clientState = null
        loadedFor = null
        editorValue = ""
        serverNotation = null
        saving = false
        lastError = null
        viewMode = CustomGlobal.current().viewMode
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        CustomGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
        CustomGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }
        syncFromClientState(clientState)
    }


    override fun onCustomState(state: CustomState) {
        setState {
            viewMode = state.viewMode
        }
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
            val freshEditorValue = ClientContext.notationParser.unparseDocument(newServerNotation, "")
            setState {
                loadedFor = documentPath
                editorValue = freshEditorValue
                serverNotation = newServerNotation
                lastError = null
            }
            CustomGlobal.setEditorModified(false)
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
            CustomGlobal.setEditorModified(false)
        }
        else {
            setState {
                serverNotation = newServerNotation
            }
        }
    }


    private fun isEditorModified(): Boolean {
        return isEditorModifiedFor(state.editorValue)
    }


    private fun isEditorModifiedFor(editorValue: String): Boolean {
        val serverNotation = state.serverNotation
            ?: return false

        return try {
            val parsed = ClientContext.notationParser.parseDocumentObjects(editorValue)
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
        CustomGlobal.setEditorModified(isEditorModifiedFor(newValue))
    }


    private fun onSave() {
        val documentPath = state.loadedFor
            ?: return

        if (state.saving) {
            return
        }

        if (!isEditorModified()) {
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
                    CustomGlobal.setEditorModified(false)
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

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            when (state.viewMode) {
                CustomViewMode.Raw ->
                    CustomRaw::class.react {
                        editorValue = state.editorValue
                        modified = isEditorModified()
                        saving = state.saving
                        lastError = state.lastError
                        onEditorChange = ::onEditorChange
                        onSave = ::onSave
                    }

                CustomViewMode.View -> {
                    val serverNotation = state.serverNotation
                    if (serverNotation != null) {
                        CustomView::class.react {
                            this.documentPath = documentPath
                            this.clientState = clientState
                            this.serverNotation = serverNotation
                            this.attributeEditorManager = props.attributeEditorManager
                        }
                    }
                }
            }
        }
    }
}
