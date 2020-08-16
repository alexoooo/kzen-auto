package tech.kzen.auto.client.objects.document.common

import kotlinx.css.em
import kotlinx.css.fontSize
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.*
import react.dom.br
import react.dom.div
import tech.kzen.auto.client.objects.document.script.step.attribute.SelectStepEditor
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
    class Props(
        var labelOverride: String?,
        var disabled: Boolean,
        var onChange: ((AttributeNotation) -> Unit)?,
        var invalid: Boolean,

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
            .transitiveAttribute(objectLocation, attributePath)

        checkNotNull(attributeNotation) {
            "missing: $objectLocation | $attributePath"
        }

        return attributeNotation
    }


    class State(
        var value: String?,
        var values: List<String>?,

        var pending: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        submitEditAsync()
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
//    override fun componentDidMount() {
//        async {
//            ClientContext.executionRepository.observe(this)
//        }
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.executionRepository.unobserve(this)
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun componentDidUpdate(
//        prevProps: Props,
//        prevState: State,
//        snapshot: Any
//    ) {
//        if (props.objectLocation != prevProps.objectLocation) {
//            state.init(props)
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun flush() {
//        println("ParameterEditor | flush")

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

//        console.log("onValueChange")

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

//        console.log("onValueChange")
        submitDebounce.apply()
    }


    private fun submitEditAsync() {
        async {
            submitEdit()
        }
    }


    private suspend fun submitEdit() {
        val attributeNotation =
            if (state.value != null) {
                ScalarAttributeNotation(state.value!!)
            }
            else {
                ListAttributeNotation(state
                    .values!!
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

        val results = Regex("[A-Z][a-z]*").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val attributeMetadata: AttributeMetadata = props
//            .clientState
//            .graphStructure()
//            .graphMetadata
//            .get(props.objectLocation)
//            ?.attributes
//            ?.get(props.attributePath.attribute)
//            ?: return

//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

        val type = props.valueType
//        val type = attributeMetadata.type

        if (type.className == ClassNames.kotlinString ||
            type.className == ClassNames.kotlinInt ||
            type.className == ClassNames.kotlinDouble
        ) {
            val textValue = state.value ?: ""
            renderString(textValue)
        }
        else if (type.className == ClassNames.kotlinList) {
            val listGeneric = type.generics.getOrNull(0)
            if (listGeneric?.className?.let { ClassNames.isPrimitive(it) } == true) {
                val textValues = state.values ?: listOf()
                renderListOfPrimitive(textValues)
            }
            else {
                +"List of: ${listGeneric?.className ?: ClassNames.kotlinAny}"
            }
        }
        else if (type.className == ClassNames.kotlinBoolean) {
            val booleanValue = state.value == "true"
            renderBoolean(booleanValue)
        }
        else {
            +"${props.attributePath} (type not supported)"

            div {
//                +"type: ${props.attributeMetadata.type?.className?.get()}"
                +"type: ${type.className.get()}"
                br {}
                +"generics: ${type.generics.map { it.className.get() }}"
            }

//            +"${props.attributeName} - $attributeNotation"
        }
    }


    private fun RBuilder.renderString(stateValue: String) {
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true

                label = formattedLabel()
                value = stateValue

                // https://stackoverflow.com/questions/54052525/how-to-change-material-ui-textfield-bottom-and-label-color-on-error-and-on-focus
//                InputLabelProps = NestedInputLabelProps(reactStyle {
//                    color = Color("rgb(66, 66, 66)")
//                })

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
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
//        console.log("%%%%%%%% renderListOfPrimitive: $stateValues")

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
            }
        }
    }
}