package tech.kzen.auto.client.objects.document.common


import kotlinx.css.em
import kotlinx.css.fontSize
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.*
import react.dom.br
import react.dom.div
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.toPersistentList


class DefaultAttributeEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, DefaultAttributeEditor.State>(props),
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val wrapperName = ObjectName("DefaultAttributeEditor")
    }


    class State(
            var value: String?,
            var values: List<String>?,

            var submitDebounce: FunctionWithDebounce,
            var pending: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation
    ):
            AttributeEditorWrapper(objectLocation)
    {
        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>): ReactElement {
            return input.child(DefaultAttributeEditor::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: AttributeEditorProps) {
//        console.log("ParameterEditor | State.init - ${props.name}")

        @Suppress("MoveVariableDeclarationIntoWhen")
//        val attributeNotation = props.attributeNotation
        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
                props.objectLocation, props.attributeName)

        when (attributeNotation) {
            is ScalarAttributeNotation -> {
                val scalarValue = attributeNotation.value

                value = scalarValue
                values = null
            }

            is ListAttributeNotation -> {
                if (attributeNotation.values.all { it.asString() != null }) {
                    val stringValues = attributeNotation.values.map { it.asString()!! }

                    value = null
                    values = stringValues
                }
            }
        }

        submitDebounce = lodash.debounce({
            editAttributeCommandAsync()
        }, 1000)

        pending = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.executionManager.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.executionManager.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
        flush()
    }


    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {}


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun flush() {
//        println("ParameterEditor | flush")

        state.submitDebounce.cancel()
        if (state.pending) {
            editAttributeCommand()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
            pending = true
        }

//        console.log("onValueChange")

        state.submitDebounce.apply()
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

        state.submitDebounce.apply()
    }



    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        if (state.value != null) {
            ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                    props.objectLocation,
                    props.attributeName,
                    ScalarAttributeNotation(state.value!!)))
        }
        else {
            ClientContext.mirroredGraphStore.apply(UpsertAttributeCommand(
                    props.objectLocation,
                    props.attributeName,
                    ListAttributeNotation(
                            state.values!!.map { ScalarAttributeNotation(it) }.toPersistentList()
                    )
            ))
        }

        setState {
            pending = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val attributeMetadata: AttributeMetadata = props
                .graphStructure
                .graphMetadata
                .get(props.objectLocation)
                ?.attributes
                ?.get(props.attributeName)
                ?: return

//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

//        val type = props.attributeMetadata.type
        val type = attributeMetadata.type

        if (type == null) {
//            +"${props.attributeName} - ${props.attributeNotation}"
            +"${props.attributeName} (type missing)"
        }
        else if (type.className == ClassNames.kotlinString ||
                type.className == ClassNames.kotlinInt) {
//            val textValue = props.attributeNotation?.asString() ?: ""
//            val textValue = attributeNotation?.asString() ?: ""
            val textValue = state.value ?: ""
            renderString(textValue)
        }
        else if (type.className == ClassNames.kotlinList) {
            val listGeneric = type.generics.getOrNull(0)
            if (listGeneric?.className == ClassNames.kotlinString) {
//                val textValues = (props.attributeNotation as ListAttributeNotation)
//                val textValues = (attributeNotation as ListAttributeNotation)
//                        .values.map { it.asString() ?: "" }
                val textValues = state.values ?: listOf()

                renderListOfString(textValues)
            }
        }
        else if (type.className == ClassNames.kotlinBoolean) {
            val booleanValue = state.value == "true"
            renderBoolean(booleanValue)
        }
        else {
            +"${props.attributeName} (type not supported)"

            div {
//                +"type: ${props.attributeMetadata.type?.className?.get()}"
                +"type: ${attributeMetadata.type?.className?.get()}"
                br {}
                +"generics: ${attributeMetadata.type?.generics?.map { it.className.get() }}"
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


    private fun RBuilder.renderListOfString(stateValues: List<String>) {
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


    private fun formattedLabel(): String {
        val upperCamelCase = props.attributeName.value.capitalize()

        val results = Regex("[A-Z][a-z]*").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }
}
