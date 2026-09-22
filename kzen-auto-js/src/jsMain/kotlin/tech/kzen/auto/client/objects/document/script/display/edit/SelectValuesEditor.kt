package tech.kzen.auto.client.objects.document.script.display.edit


import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface SelectValuesEditorState: State {
    // Ordered key -> label options, read from the attribute's `meta.<attr>.values` map notation.
    var options: Map<String, String>?

    // Optional key -> one-line explanation, from the sibling `meta.<attr>.details` map; drawn under the option.
    var details: Map<String, String>?
    var label: String?
    var value: String?
}


//---------------------------------------------------------------------------------------------------------------------
// Generic notation-driven enum select: renders a labelled dropdown whose options come from the attribute's
// `meta.<attr>.values` map (key = stored value, entry = display label) — so any String attribute with a fixed
// value set reuses this editor declaratively, with no per-enum Kotlin (replaced the bespoke close-policy editor).
@Suppress("unused")
class SelectValuesEditor(
    props: AttributeEditorProps
):
    ObjectScopedComponent<AttributeEditorProps, SelectValuesEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectValuesEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val valuesAttributePath = AttributePath.parse("values")
        private val detailsAttributePath = AttributePath.parse("details")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()

        val valuesNotation = graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?.get(valuesAttributePath.toNesting())
                as? MapAttributeNotation

        val options = valuesNotation
            ?.map
            ?.entries
            ?.associate { (key, label) -> key.asKey() to (label.asString() ?: key.asKey()) }

        val details = (graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?.get(detailsAttributePath.toNesting())
                as? MapAttributeNotation)
            ?.map
            ?.entries
            ?.mapNotNull { (key, detail) -> detail.asString()?.let { key.asKey() to it } }
            ?.toMap()

        val label = CommonEditUtils.declaredLabel(graphStructure, props.objectLocation, props.attributeName)

        val value = (graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value

        if (state.options == options && state.value == value &&
            state.details == details && state.label == label) {
            return
        }

        setState {
            this.options = options
            this.details = details
            this.label = label
            this.value = value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Write only on a genuine user change — no componentDidUpdate write, so the mount-time hydration is never
    // echoed back to the notation as a no-op command (same discipline the bespoke close-policy editor established).
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
        val options = state.options
            ?: return

        val selectOptions: Array<SelectOption> = options
            .map { (key, label) ->
                val option: SelectOption = unsafeJso {
                    this.value = key
                    this.label = label
                    state.details?.get(key)?.let { this.detail = it }
                }
                option
            }
            .toTypedArray()

        muiAutocompleteField(
            label = CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName), state.label),
            options = selectOptions,
            selectedOption = selectOptions.find { it.value == state.value },
            onSelect = { onValueChange(it.value) },
            disableClearable = true)
    }
}
