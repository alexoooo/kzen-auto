package tech.kzen.auto.client.objects.document.script.display.edit


import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
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
    RPureComponent<AttributeEditorProps, SelectValuesEditorState>(props),
    ClientStateGlobal.Observer
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()

        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: containing step was renamed or deleted; parent re-render will swap props.objectLocation shortly
            return
        }

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

        val value = (graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value

        if (state.options == options && state.value == value) {
            return
        }

        setState {
            this.options = options
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
                }
                option
            }
            .toTypedArray()

        muiAutocompleteField(
            label = CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName)),
            options = selectOptions,
            selectedOption = selectOptions.find { it.value == state.value },
            onSelect = { onValueChange(it.value) },
            disableClearable = true)
    }
}
