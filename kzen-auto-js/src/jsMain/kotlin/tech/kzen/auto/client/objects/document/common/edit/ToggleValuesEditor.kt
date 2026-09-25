package tech.kzen.auto.client.objects.document.common.edit


import emotion.react.css
import mui.material.InputLabel
import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import mui.system.sx
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ToggleValuesEditorState: State {
    // Ordered key -> label options, read from the attribute's `meta.<attr>.values` map notation.
    var options: Map<String, String>?

    // Optional key -> one-line explanation, from the sibling `meta.<attr>.details` map; the button's tooltip.
    var details: Map<String, String>?
    var label: String?
    var value: String?
}


//---------------------------------------------------------------------------------------------------------------------
// The button-group twin of SelectValuesEditor, for a short fixed value set where every choice should be visible at
// once: same `meta.<attr>.values` / `details` notation, drawn as an exclusive toggle group. A click on the selected
// button does nothing, so the attribute never becomes empty.
@Suppress("unused")
class ToggleValuesEditor(
    props: AttributeEditorProps
):
    ObjectScopedComponent<AttributeEditorProps, ToggleValuesEditorState>(props)
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
            ToggleValuesEditor::class.react {
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

        val attributeMetadataNotation = graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation

        val options = (attributeMetadataNotation?.get(valuesAttributePath.toNesting()) as? MapAttributeNotation)
            ?.map
            ?.entries
            ?.associate { (key, label) -> key.asKey() to (label.asString() ?: key.asKey()) }

        val details = (attributeMetadataNotation?.get(detailsAttributePath.toNesting()) as? MapAttributeNotation)
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
    // Write only on a genuine user change, so mount-time hydration is never echoed back as a no-op command
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

        div {
            css {
                marginTop = 0.25.em
            }

            InputLabel {
                sx {
                    fontSize = 0.8.em
                }
                +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName), state.label)
            }

            ToggleButtonGroup {
                value = state.value
                exclusive = true
                size = Size.small

                // Null when the selected button is clicked again: keep the value rather than clear it
                asDynamic()["onChange"] = { _: Any?, next: Any? ->
                    if (next is String) {
                        onValueChange(next)
                    }
                }

                for ((key, label) in options) {
                    ToggleButton {
                        value = key
                        state.details?.get(key)?.let { title = it }
                        +label
                    }
                }
            }
        }
    }
}
