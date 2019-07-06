package tech.kzen.auto.client.objects.document.script.action



import kotlinx.css.em
import kotlinx.css.fontSize
import react.*
import tech.kzen.auto.client.objects.document.common.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.AttributeEditorWrapper
import tech.kzen.auto.client.objects.document.script.ScriptController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.notation.edit.UpsertAttributeCommand
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import kotlin.js.Json
import kotlin.js.json


@Suppress("unused")
class SelectStepEditor(
        props: AttributeEditorProps
):
        RPureComponent<AttributeEditorProps, SelectStepEditor.State>(props)/*,
        ExecutionManager.Observer*/
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var value: ObjectLocation?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation
    ):
            AttributeEditorWrapper(objectLocation)
    {
        override fun child(input: RBuilder, handler: RHandler<AttributeEditorProps>): ReactElement {
            return input.child(SelectStepEditor::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: AttributeEditorProps) {
//        console.log("ParameterEditor | State.init - ${props.name}")

        @Suppress("MoveVariableDeclarationIntoWhen")
        val attributeNotation =
//                props.attributeNotation
                props.graphStructure.graphNotation.transitiveAttribute(
                        props.objectLocation, props.attributeName)

        if (attributeNotation is ScalarAttributeNotation) {
            value = ObjectLocation.parse(attributeNotation.value)
        }

//        when (attributeNotation) {
//            is ScalarAttributeNotation -> {
//                val scalarValue = attributeNotation.value
//
//                value = scalarValue
//                values = null
//            }
//
//            is ListAttributeNotation -> {
//                if (attributeNotation.values.all { it.asString() != null }) {
//                    val stringValues = attributeNotation.values.map { it.asString()!! }
//
//                    value = null
//                    values = stringValues
//                }
//            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
            prevProps: AttributeEditorProps,
            prevState: State,
            snapshot: Any
    ) {
        if (state.value != prevState.value) {
            editAttributeCommandAsync()
        }
    }

//    override fun componentDidMount() {
//        async {
//            ClientContext.executionManager.observe(this)
//        }
//    }
//
//
//    override fun componentWillUnmount() {
//        ClientContext.executionManager.unobserve(this)
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {
//        flush()
//    }
//
//
//    override suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel) {}


    //-----------------------------------------------------------------------------------------------------------------
//    private suspend fun flush() {
////        println("ParameterEditor | flush")
//
//        state.submitDebounce.cancel()
//        if (state.pending) {
//            editAttributeCommand()
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: ObjectLocation?) {
        console.log("onValueChange - $value")

        setState {
            this.value = value
        }

//        setState {
//            this.value = value
//            pending = true
//        }


//        state.submitDebounce.apply()
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val value = state.value
                ?: return

        ClientContext.commandBus.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(value.asString())))

//        setState {
//            pending = false
//        }
    }


    private fun predecessors(): List<ObjectLocation> {
        val stepLocations = ScriptController.stepLocations(
                props.graphStructure, props.objectLocation.documentPath)!!

        return stepLocations.takeWhile { it != props.objectLocation }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                props.objectLocation, props.attributeName)

        val selectOptions = predecessors()
                .map { ReactSelectOption(it.asString(), it.objectPath.name.value) }
                .toTypedArray()

//        +"^^ SELECT: ${props.attributeName} - $attributeNotation - ${selectOptions.map { it.value }}"

        val selectId = "material-react-select-id"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                }
            }

            +formattedLabel()
//            +"Value"
        }

//        val firstOption = ReactSelectOption("foo", "Foo")
//        val secondOption = ReactSelectOption("bar", "Boo")
//        val optionsArray: Array<ReactSelectOption> = arrayOf(firstOption, secondOption)

        child(ReactSelect::class) {
            attrs {
                id = selectId

                value = selectOptions.find { it.value == state.value?.asString() }
//                value = firstOption

                options = selectOptions
//                options = optionsArray

                onChange = {
//                    console.log("^^^^^ selected: $it")

                    onValueChange(ObjectLocation.parse(it.value))
//                    onTypeChange(it.value)
                }

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, state ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed
                }

                val reactStyles = json()
                reactStyles["control"] = styleTransformer
                styles = reactStyles
            }
        }
    }


//    private fun RBuilder.renderString(stateValue: String) {
//        child(MaterialTextField::class) {
//            attrs {
//                fullWidth = true
//
//                label = formattedLabel()
//                value = stateValue
//
//                // https://stackoverflow.com/questions/54052525/how-to-change-material-ui-textfield-bottom-and-label-color-on-error-and-on-focus
////                InputLabelProps = NestedInputLabelProps(reactStyle {
////                    color = Color("rgb(66, 66, 66)")
////                })
//
//                onChange = {
//                    val target = it.target as HTMLInputElement
//                    onValueChange(target.value)
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderListOfString(stateValues: List<String>) {
//        child(MaterialTextField::class) {
//            attrs {
//                fullWidth = true
//                multiline = true
//
//                label = formattedLabel() + " (one per line)"
//                value = stateValues.joinToString("\n")
//
//                onChange = {
//                    val target = it.target as HTMLTextAreaElement
//                    val lines = target.value.split(Regex("\\n+"))
//                    val values =
//                            if (lines.size == 1 && lines[0].isEmpty()) {
//                                listOf()
//                            }
//                            else {
//                                lines
//                            }
//                    onValuesChange(values)
//                }
//            }
//        }
//    }


    private fun formattedLabel(): String {
        val upperCamelCase = props.attributeName.value.capitalize()

        val results = Regex("[A-Z][a-z]*").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }
}
