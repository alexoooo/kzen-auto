package tech.kzen.auto.client.objects.document.job.edit

import react.ChildrenBuilder
import react.State
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
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface DataFormatEditorProps: AttributeEditorProps {
    var kind: DataFormatEditor.Kind
}


external interface DataFormatEditorState: State {
    var catalog: FileFormatCatalog?
    var value: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Select for a data source's default format or text encoding, offering what the server actually has installed.
 *
 * These were free-text fields, which asked the user to know a format coordinate or a charset name by heart and
 * turned a typo into a run-time failure with no hint of the spelling that would have worked. The option lists
 * come from the document's shared [DataFormatStore] — the same catalogue the per-file overrides under a
 * selection's Details use, so a Worker's default and a file's override always name formats the same way.
 *
 * One component behind two notation registrations: the two attributes differ only in which half of the catalogue
 * they read, and splitting that into two near-identical classes would only duplicate the notation reading, the
 * store subscription and the write.
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
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun DataFormatEditorState.init(props: DataFormatEditorProps) {
        catalog = null
        value = null
    }


    override fun componentDidMount() {
        super.componentDidMount()
        dataFormatStore()?.observe(this)
    }


    override fun componentWillUnmount() {
        dataFormatStore()?.unobserve(this)
        super.componentWillUnmount()
    }


    // Absent when this attribute is edited outside a Job stage: the select then offers Default plus whatever is
    // already configured, which is also what a failed fetch leaves — never an empty, uneditable field.
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val value = state.value
            ?: return

        val options = when (props.kind) {
            Kind.Format -> DataFormatOptions.formats(state.catalog, value)
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
