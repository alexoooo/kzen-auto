package tech.kzen.auto.client.objects.document.common

import kotlinx.css.em
import kotlinx.css.fontSize
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.toPersistentList


class AttributePathValueEditor(
    props: Props
):
    RPureComponent<AttributePathValueEditor.Props, AttributePathValueEditor.State>(props)
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
    class Props(
        var labelOverride: String?,
        var multilineOverride: Boolean?,
        var disabled: Boolean,
        var invalid: Boolean,
        var onChange: ((AttributeNotation) -> Unit)?,

        var clientState: SessionState,
        var objectLocation: ObjectLocation,
        var attributePath: AttributePath,

        var valueType: TypeMetadata
    ): RProps


    private fun Props.valuesAttribute(): AttributeNotation {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val attributeNotation = clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(objectLocation, attributePath)

        checkNotNull(attributeNotation) {
            "missing: $objectLocation | $attributePath"
        }

//        val merge = clientState
//            .graphStructure()
//            .graphNotation
//            .mergeAttribute(objectLocation, attributePath)!!
//        console.log("#!@#@! merge: $objectLocation - $attributePath - $merge")

        return attributeNotation
    }


    class State(
        var value: String?,
        var values: List<String>?,

        var pending: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        val attributeNotation = props.valuesAttribute()

        val (value, values) = extractValues(attributeNotation)

        this.value = value
        this.values = values

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
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (props.clientState == prevProps.clientState) {
            return
        }

        val attributeNotation = props.valuesAttribute()
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

        val command =
            if (props.attributePath.nesting.segments.isEmpty()) {
                UpsertAttributeCommand(
                    props.objectLocation,
                    props.attributePath.attribute,
                    attributeNotation)
            }
            else {
                UpdateInAttributeCommand(
                    props.objectLocation,
                    props.attributePath,
                    attributeNotation)
            }

        // TODO: handle error
        ClientContext.mirroredGraphStore.apply(command)

        setState {
            pending = false
        }

        props.onChange?.invoke(attributeNotation)
    }


    private fun formattedLabel(): String {
        val labelOverride = props.labelOverride
        if (labelOverride != null) {
            return labelOverride
        }

        val defaultLabel =
            if (props.attributePath.nesting.segments.isEmpty()) {
                props.attributePath.attribute.value
            }
            else {
                props.attributePath.nesting.segments.last().asString()
            }

        val upperCamelCase = defaultLabel.capitalize()

        val results = Regex("\\w+").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +"## attributePath ${props.attributePath} - state.value ${state.value}"

        val type = props.valueType
//        val type = attributeMetadata.type

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


    private fun RBuilder.renderString(stateValue: String) {
        val multilineOverride = props.multilineOverride ?: false
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true
                multiline = multilineOverride

                label = formattedLabel()
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
    }


    private fun RBuilder.renderBoolean(stateValue: Boolean) {
        val inputId = "material-react-switch-id"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = inputId

                style = reactStyle {
                    fontSize = 0.8.em
                }
            }

            +formattedLabel()
        }

        child(MaterialSwitch::class) {
            attrs {
                id = inputId

                checked = stateValue

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.checked.toString())
                }
            }
        }
    }


    private fun RBuilder.renderListOfPrimitive(stateValues: List<String>) {
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true
                multiline = true

                label = formattedLabel() + " (one per line)"
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
}