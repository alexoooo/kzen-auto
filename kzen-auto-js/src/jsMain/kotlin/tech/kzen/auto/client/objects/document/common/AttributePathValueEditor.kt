package tech.kzen.auto.client.objects.document.common

import mui.material.*
import mui.system.sx
import react.ChildrenBuilder
import react.PropsWithRef
import react.ReactNode
import react.State
import react.dom.events.ChangeEvent
import react.dom.onChange
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.toPersistentList
import web.cssom.NamedColor
import web.cssom.em
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface AttributePathValueEditorProps: PropsWithRef<AttributePathValueEditor> {
    var labelOverride: String?
    var multilineOverride: Boolean?
    var disabled: Boolean
    var invalid: Boolean
    var onChange: ((AttributeNotation) -> Unit)?

    var objectLocation: ObjectLocation
    var attributePath: AttributePath

    var valueType: TypeMetadata
}


external interface AttributePathValueEditorState: State {
    var value: String?
    var values: List<String>?
    var pending: Boolean

    var attributeNotation: AttributeNotation?
}


//---------------------------------------------------------------------------------------------------------------------
class AttributePathValueEditor(
    props: AttributePathValueEditorProps
):
    RPureComponent<AttributePathValueEditorProps, AttributePathValueEditorState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun isValue(typeMetadata: TypeMetadata): Boolean {
            val className = typeMetadata.className
            if (ClassNames.isPrimitive(className)) {
                return true
            }

            val isContainer =
                className == ClassNames.kotlinList ||
                className == ClassNames.kotlinSet

            if (! isContainer) {
                return false
            }

            val containerGeneric = typeMetadata.generics.getOrNull(0)
                ?: return false

            return ClassNames.isPrimitive(containerGeneric.className)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()

        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: containing step was deleted, but its parent component hasn't re-rendered yet
            return
        }

        val attributeNotation: AttributeNotation? = graphStructure
            .graphNotation
            .mergeAttribute(props.objectLocation, props.attributePath)

        if (state.attributeNotation == attributeNotation) {
            return
        }

        setState {
            this.attributeNotation = attributeNotation

            if (attributeNotation != null) {
                val (value, values) = extractValues(attributeNotation)
                this.value = value
                this.values = values
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun AttributePathValueEditorState.init(props: AttributePathValueEditorProps) {
//        val attributeNotation = props.valuesAttribute()
//
//        val (value, values) = extractValues(attributeNotation)
//
//        this.value = value
//        this.values = values
        value = null
        values = null
        pending = false
    }


    private fun extractValues(attributeNotation: AttributeNotation): Pair<String?, List<String>?> {
        return when (attributeNotation) {
            is ScalarAttributeNotation -> {
                val scalarValue = attributeNotation.value
                scalarValue to null
            }

            is ListAttributeNotation -> {
                if (attributeNotation.values.all { it.asString() != null }) {
                    val stringValues = attributeNotation.values.map { it.asString()!! }

                    null to stringValues
                }
                else {
                    null to null
                }
            }

            is MapAttributeNotation -> TODO()
        }
    }


    override fun componentDidUpdate(
        prevProps: AttributePathValueEditorProps,
        prevState: AttributePathValueEditorState,
        snapshot: Any
    ) {
        if (state.attributeNotation == prevState.attributeNotation) {
            return
        }

        val attributeNotation = state.attributeNotation
        if (attributeNotation == null) {
            setState {
                value = null
                values = null
            }
            return
        }

        val (value, values) = extractValues(attributeNotation)

        if (value != state.value || values != state.values) {
            setState {
                this.value = value
                this.values = values
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun flush() {
        submitDebounce.cancel()
        if (state.pending) {
            submitEdit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
            pending = true
        }

        submitDebounce.apply()
    }


    private fun onValuesChange(newValues: List<String>) {
        if (state.values == newValues) {
            return
        }

        setState {
            values = newValues
            pending = true
        }

        submitDebounce.apply()
    }


    private suspend fun submitEdit() {
        val attributeNotation =
            if (state.value != null) {
                ScalarAttributeNotation(state.value!!)
            }
            else {
                val values = state.values!!
                val isSet = props.valueType.className == ClassNames.kotlinSet
                val adjustedValues =
                    if (isSet) {
                        values.toSet().toList()
                    }
                    else {
                        values
                    }

                ListAttributeNotation(adjustedValues
                    .map { ScalarAttributeNotation(it) }
                    .toPersistentList())
            }

        val command = CommonEditUtils.editCommand(
            props.objectLocation, props.attributePath, attributeNotation)

        // TODO: handle error
        ClientContext.mirroredGraphStore.apply(command)

        setState {
            pending = false
        }

        props.onChange?.invoke(attributeNotation)
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(props.attributePath, props.labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        +"## attributePath ${props.attributePath} - state.value ${state.value}"

        val type = props.valueType

        if (! isValue(type)) {
            +"${props.attributePath} $type (not a value)"
        }
        else if (
            type.className == ClassNames.kotlinString ||
            type.className == ClassNames.kotlinInt ||
            type.className == ClassNames.kotlinLong ||
            type.className == ClassNames.kotlinDouble
        ) {
            val textValue = state.value ?: ""
            renderString(textValue)
        }
        else if (type.className == ClassNames.kotlinBoolean) {
            val booleanValue = state.value == "true"
            renderBoolean(booleanValue)
        }
        else {
            val isList = type.className == ClassNames.kotlinList
            val isSet = type.className == ClassNames.kotlinSet
            check(isList || isSet)

            val textValues = state.values ?: listOf()
            renderListOfPrimitive(textValues)
        }
    }


    private fun ChildrenBuilder.renderString(stateValue: String) {
        val multilineOverride = props.multilineOverride ?: false

        TextField {
            fullWidth = true
            multiline = multilineOverride
            size = Size.small

            label = ReactNode(formattedLabel())
            value = stateValue

            // https://stackoverflow.com/questions/54052525/how-to-change-material-ui-textfield-bottom-and-label-color-on-error-and-on-focus
//                InputLabelProps = NestedInputLabelProps(reactStyle {
//                    color = Color("rgb(66, 66, 66)")
//                })

            onChange = {
                val value =
                    if (multilineOverride) {
                        (it.target as HTMLTextAreaElement).value
                    }
                    else {
                        (it.target as HTMLInputElement).value
                    }

                onValueChange(value)
            }

            disabled = props.disabled
            error = props.invalid
        }
    }


    private fun ChildrenBuilder.renderBoolean(stateValue: Boolean) {
        InputLabel {
            sx {
                fontSize = 0.8.em
            }

            +formattedLabel()

            Switch {
                checked = stateValue

                onChange = { event: ChangeEvent<HTMLInputElement>, _: Boolean ->
                    val target = event.target
                    onValueChange(target.checked.toString())
                }

                color = SwitchColor.default

                if (stateValue) {
                    sx {
                        color = NamedColor.black
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderListOfPrimitive(stateValues: List<String>) {
        TextField {
            fullWidth = true
            multiline = true
            size = Size.small

            label = ReactNode(formattedLabel() + " (one per line)")
            value = stateValues.joinToString("\n")

            onChange = {
                val target = it.target as HTMLTextAreaElement
                val lines = target.value.split(Regex("\\n+"))
                val values =
                    if (lines.size == 1 && lines[0].isEmpty()) {
                        listOf()
                    }
                    else {
                        lines
                    }

                onValuesChange(values)
            }

            disabled = props.disabled
            error = props.invalid
        }
    }
}