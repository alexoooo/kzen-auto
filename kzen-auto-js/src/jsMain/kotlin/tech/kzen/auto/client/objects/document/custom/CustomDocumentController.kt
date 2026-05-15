package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.CardContent
import mui.material.Paper
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.edit.YamlEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomDocumentControllerProps: Props {
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomDocumentControllerState: State {
    var clientState: ClientState?
    var loadedFor: DocumentPath?
    var editorValue: String
    var serverNotation: DocumentObjectNotation?
    var saving: Boolean
    var lastError: String?
    var viewMode: CustomDocumentViewMode
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomDocumentController(
    props: CustomDocumentControllerProps
):
    RPureComponent<CustomDocumentControllerProps, CustomDocumentControllerState>(props),
    ClientStateGlobal.Observer,
    CustomDocumentGlobal.Observer
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
                    CustomDocumentHeader::class.react {
                        block()
                    }
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    CustomDocumentController::class.react {
                        this.attributeEditorManager = this@Wrapper.attributeEditorManager
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomDocumentControllerState.init(props: CustomDocumentControllerProps) {
        clientState = null
        loadedFor = null
        editorValue = ""
        serverNotation = null
        saving = false
        lastError = null
        viewMode = CustomDocumentGlobal.current().viewMode
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        CustomDocumentGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
        CustomDocumentGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }
        syncFromClientState(clientState)
    }


    override fun onCustomDocumentState(state: CustomDocumentState) {
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
            CustomDocumentGlobal.setEditorModified(false)
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
            CustomDocumentGlobal.setEditorModified(false)
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
        CustomDocumentGlobal.setEditorModified(isEditorModifiedFor(newValue))
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
                    CustomDocumentGlobal.setEditorModified(false)
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
                CustomDocumentViewMode.Raw ->
                    renderRaw()

                CustomDocumentViewMode.View ->
                    renderView(documentPath, clientState)
            }
        }
    }


    private fun ChildrenBuilder.renderRaw() {
        val modified = isEditorModified()
        val saveDisabled = !modified || state.saving

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


    private fun ChildrenBuilder.renderView(documentPath: DocumentPath, clientState: ClientState) {
        val serverNotation = state.serverNotation
            ?: return

        val graphMetadata = clientState.graphStructure().graphMetadata

        for ((objectPath, _) in serverNotation.notations.map) {
            if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                continue
            }

            val objectLocation = ObjectLocation(documentPath, objectPath)
            val objectMetadata = graphMetadata.objectMetadata[objectLocation]

            div {
                css {
                    marginBottom = 1.em
                }

                renderObjectCard(objectPath, objectLocation, objectMetadata)
            }
        }
    }


    private fun ChildrenBuilder.renderObjectCard(
        objectPath: ObjectPath,
        objectLocation: ObjectLocation,
        objectMetadata: ObjectMetadata?
    ) {
        Paper {
            sx {
                backgroundColor = NamedColor.white
            }

            CardContent {
                div {
                    css {
                        fontWeight = FontWeight.bold
                        fontSize = 1.1.em
                        marginBottom = 0.75.em
                    }
                    +objectPath.name.value
                }

                if (objectMetadata == null) {
                    div {
                        css {
                            fontStyle = FontStyle.italic
                            color = Color("rgb(128, 80, 0)")
                        }
                        +"(metadata unavailable)"
                    }
                }
                else {
                    renderAttributes(objectLocation, objectMetadata)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderAttributes(
        objectLocation: ObjectLocation,
        objectMetadata: ObjectMetadata
    ) {
        for (entry in objectMetadata.attributes.map) {
            val attributeName = entry.key
            if (AutoConventions.isManaged(attributeName)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                props.attributeEditorManager.child(this) {
                    this.objectLocation = objectLocation
                    this.attributeName = attributeName
                }
            }
        }
    }
}
