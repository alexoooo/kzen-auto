package tech.kzen.auto.client.objects.document.script.display.edit


import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface SelectClosePolicyEditorState: State {
    var policy: ResourceClosePolicy?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectClosePolicyEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, SelectClosePolicyEditorState>(props),
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
            SelectClosePolicyEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // One descriptive line per policy — shown in the dropdown list and the closed select box.
        private fun optionLabel(policy: ResourceClosePolicy): String {
            return when (policy) {
                ResourceClosePolicy.Auto ->
                    "Auto — close when the run finishes (success, failure, or cancel)"

                ResourceClosePolicy.Manual ->
                    "Manual — keep open; only an explicit close step disposes it"

                ResourceClosePolicy.KeepOnFailure ->
                    "Keep on failure — close on success/cancel, keep on a failed run to inspect"
            }
        }
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
            // NB: containing step was deleted, but the parent component hasn't re-rendered yet
            return
        }

        val notation = graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation
            ?: return

        val policy = runCatching { ResourceClosePolicy.parse(notation.value) }.getOrNull()
            ?: return

        if (state.policy == policy) {
            return
        }

        setState {
            this.policy = policy
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Write only on a genuine user change — there is no componentDidUpdate write, so the mount-time
    // onClientState hydration is never echoed back to the notation as a no-op command.
    private fun onPolicyChange(newPolicy: ResourceClosePolicy) {
        if (state.policy == newPolicy) {
            return
        }

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(newPolicy.key)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val policy = state.policy
            ?: return

        val options = ResourceClosePolicy.entries
            .map { option ->
                val selectOption: SelectOption = unsafeJso {
                    value = option.key
                    label = optionLabel(option)
                }
                selectOption
            }
            .toTypedArray()

        muiAutocompleteField(
            label = "Close policy",
            options = options,
            selectedOption = options.find { it.value == policy.key },
            onSelect = { onPolicyChange(ResourceClosePolicy.parse(it.value)) },
            disableClearable = true)
    }
}
