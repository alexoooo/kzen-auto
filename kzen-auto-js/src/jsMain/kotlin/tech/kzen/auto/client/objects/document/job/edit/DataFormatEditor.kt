package tech.kzen.auto.client.objects.document.job.edit

import react.ChildrenBuilder
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
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
/**
 * Selects an explicit shared format or one format-owned text encoding from the server catalogue. The attribute's
 * `writable: true` metadata limits the formats to those that can write.
 */
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
    companion object {
        private const val writableKey = "writable"
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun DataFormatEditorState.init(props: DataFormatEditorProps) {
        catalog = null
        value = null
        writableOnly = false
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
        val graphStructure = clientState.graphStructure()
        val value = (graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value
            .orEmpty()
        val writableOnly = graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?.get(writableKey)
            ?.asBoolean()
            ?: false

        if (state.value == value && state.writableOnly == writableOnly) {
            return
        }

        setState {
            this.value = value
            this.writableOnly = writableOnly
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val value = state.value
            ?: return

        val options = when (props.kind) {
            Kind.Format ->
                if (state.writableOnly) DataFormatOptions.writableFormats(state.catalog, value)
                else DataFormatOptions.formats(state.catalog, value)
            Kind.Encoding -> DataFormatOptions.encodings(state.catalog, value)
        }

        muiAutocompleteField(
            label = CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName)),
            options = options,
            selectedOption = options.find { it.value == value },
            onSelect = { option: SelectOption -> onValueChange(option.value) },
            disableClearable = true)
    }
}
