package tech.kzen.auto.client.objects.document.job.edit

import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.attribute.DefaultAttributeEditor
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.job.JobValidationChannel
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobReadConventions
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface SelectPartRoleEditorState: State {
    // The part names the Worker's validation found in its units ([JobReadConventions.rolesKey]); null while unknown
    var roles: List<String>?
    var label: String?
    var value: String?
}


/**
 * "Part to read" as a choice among the part names the units actually hold, which the Worker's own validation
 * reports ([JobReadConventions.rolesKey], read off [JobValidationChannel] since an attribute editor receives only
 * its object and attribute). Where they are not known — a Logic source before Run, or before validation lands — it
 * is the ordinary text field. Blank means "the only part", so it is offered only when there is exactly one; a
 * configured name the units do not hold stays selectable, marked, so it can be seen and changed.
 */
@Suppress("unused")
class SelectPartRoleEditor(
    props: AttributeEditorProps
):
    ObjectScopedComponent<AttributeEditorProps, SelectPartRoleEditorState>(props),
    JobValidationChannel.Observer
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
            SelectPartRoleEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    private var validationChannel: JobValidationChannel? = null


    override fun SelectPartRoleEditorState.init(props: AttributeEditorProps) {
        roles = null
        label = null
        value = null
    }


    override fun componentDidMount() {
        super.componentDidMount()
        validationChannel = contextValue<DocumentBridge?>()?.channel(JobValidationChannel.Key)?.also { channel ->
            channel.observe(this)
            onJobValidation(channel.current())
        }
    }


    override fun componentWillUnmount() {
        super.componentWillUnmount()
        validationChannel?.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()
        val label = CommonEditUtils.declaredLabel(graphStructure, props.objectLocation, props.attributeName)
        val value = (graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value

        if (state.label == label && state.value == value) {
            return
        }
        setState {
            this.label = label
            this.value = value
        }
    }


    override fun onJobValidation(validation: JobValidation?) {
        val details = validation?.workerValidations?.get(props.objectLocation.objectPath)?.details.orEmpty()
        val roles = (details[JobReadConventions.rolesKey] as? List<*>)
            ?.filterIsInstance<String>()
            ?.takeIf { details[JobReadConventions.readsUnitsKey] == true && it.isNotEmpty() }

        if (state.roles == roles) {
            return
        }
        setState {
            this.roles = roles
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Written only on a genuine user change, as SelectValuesEditor does
    private fun onValueChange(newValue: String) {
        if (state.value.orEmpty() == newValue) {
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
        val roles = state.roles
        if (roles == null) {
            DefaultAttributeEditor::class.react {
                clientStateGlobal = props.clientStateGlobal
                mirroredGraphStore = props.mirroredGraphStore
                objectLocation = props.objectLocation
                attributeName = props.attributeName
            }
            return
        }

        val value = state.value.orEmpty()
        val options = mutableListOf<SelectOption>()
        if (roles.size == 1) {
            options.add(option("", "The only part (${roles.single()})", null))
        }
        for (role in roles) {
            options.add(option(role, role, null))
        }
        if (value.isNotEmpty() && value !in roles) {
            options.add(option(value, value, "Not among the parts the units hold: ${roles.joinToString()}"))
        }
        val selectOptions = options.toTypedArray()

        muiAutocompleteField(
            label = CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName), state.label),
            options = selectOptions,
            selectedOption = selectOptions.find { it.value == value },
            onSelect = { onValueChange(it.value) },
            disableClearable = true)
    }


    private fun option(value: String, label: String, detail: String?): SelectOption =
        unsafeJso {
            this.value = value
            this.label = label
            detail?.let { this.detail = it }
        }
}
