package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.file.DataFormatOptions
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.job.source.DataFormatStore
import tech.kzen.auto.client.objects.document.job.source.DataFormatStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.objects.document.custom.create.SharedCustomDocument
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.Color
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
/** Selects an explicit shared format or one format-owned text encoding from the server catalogue. */
@Suppress("unused")
class DataFormatEditor(
    props: DataFormatEditorProps
):
    ObjectScopedComponent<DataFormatEditorProps, DataFormatEditorState>(props),
    DataFormatStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    enum class Kind {
        Format,
        Encoding
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class FormatWrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            DataFormatEditor::class.react {
                kind = Kind.Format
                clientStateGlobal = this@FormatWrapper.clientStateGlobal
                mirroredGraphStore = this@FormatWrapper.mirroredGraphStore
                block()
            }
        }
    }


    @Reflect
    class EncodingWrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            DataFormatEditor::class.react {
                kind = Kind.Encoding
                clientStateGlobal = this@EncodingWrapper.clientStateGlobal
                mirroredGraphStore = this@EncodingWrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun DataFormatEditorState.init(props: DataFormatEditorProps) {
        catalog = null
        value = null
        creating = false
        createError = null
    }


    override fun componentDidMount() {
        super.componentDidMount()
        dataFormatStore()?.observe(this)
    }


    override fun componentWillUnmount() {
        dataFormatStore()?.unobserve(this)
        super.componentWillUnmount()
    }


    // Absent outside a Job stage; the field still preserves and displays its current authored value.
    private fun dataFormatStore(): DataFormatStore? {
        return contextValue<DocumentBridge?>()?.lookup(DataFormatStoreKey)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onDataFormatState(state: DataFormatStore.State) {
        val catalog = state.catalog
        if (this.state.catalog == catalog) {
            return
        }
        setState { this.catalog = catalog }
    }


    override fun onClientState(clientState: ClientState) {
        val value = (clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value
            .orEmpty()

        if (state.value == value) {
            return
        }

        setState {
            this.value = value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Written only on a genuine user change, so the mount-time hydration is never echoed back to the notation as
    // a no-op command (the SelectValuesEditor discipline).
    private fun onValueChange(newValue: String) {
        if (state.value == newValue) {
            return
        }

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(newValue)))
        }
    }


    private fun onCreateFormat() {
        if (state.creating) {
            return
        }
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return
        val graphNotation = graphStructure.graphNotation
        val metadata = graphStructure.graphMetadata.get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?: return
        val constraintText = metadata.attributeMetadataNotation
            .get(NotationConventions.isAttributePath.toNesting())
            ?.asString()
            ?: return
        val constraint = graphNotation.coalesce.locateOptional(
            ObjectReference.parse(constraintText),
            ObjectReferenceHost.ofLocation(props.objectLocation))
            ?: return
        val creation = CustomConventions.listPrototypes(graphStructure).firstOrNull { candidate ->
            constraint in graphNotation.inheritanceChain(candidate.prototype)
        } ?: return
        val customDocument = graphNotation.coalesce.locateOptional(
            ObjectReference.ofRootName(CustomConventions.customDocumentObjectName))
            ?: return
        val existingNames = graphNotation.documents.map.keys
            .filter { it.nesting == props.objectLocation.documentPath.nesting }
            .map { it.name.value }
            .toSet()
        val documentName = NextAvailableName.find(
            creation.label,
            separator = "-",
            range = 2 .. 1000) { it !in existingNames }
            ?: return
        val documentPath = DocumentPath(
            DocumentName(documentName),
            props.objectLocation.documentPath.nesting,
            false)
        val createdLocation = documentPath.toObjectLocation(
            SharedCustomDocument.objectPath(creation))

        setState {
            creating = true
            createError = null
        }
        async {
            val created = props.mirroredGraphStore.apply(CreateDocumentCommand(
                documentPath,
                SharedCustomDocument.create(customDocument, creation)))
            if (created is MirroredGraphError) {
                setState {
                    creating = false
                    createError = created.error.message ?: created.error.toString()
                }
                return@async
            }
            val selected = props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(createdLocation.toReference().asString())))
            setState {
                creating = false
                createError = (selected as? MirroredGraphError)?.error?.message
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val value = state.value
            ?: return

        val options = when (props.kind) {
            Kind.Format -> DataFormatOptions.formats(state.catalog, value)
            Kind.Encoding -> DataFormatOptions.encodings(state.catalog, value)
        }

        div {
            muiAutocompleteField(
                label = CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName)),
                options = options,
                selectedOption = options.find { it.value == value },
                onSelect = { option: SelectOption -> onValueChange(option.value) },
                disableClearable = true)

            if (props.kind == Kind.Format) {
                Button {
                    css { marginTop = 0.4.em }
                    variant = ButtonVariant.outlined
                    size = Size.small
                    disabled = state.creating
                    onClick = { onCreateFormat() }
                    +(if (state.creating) "Creating…" else "Create shared format")
                }
                state.createError?.let { error ->
                    div {
                        css { color = Color("#c62828") }
                        +error
                    }
                }
            }
        }
    }
}
