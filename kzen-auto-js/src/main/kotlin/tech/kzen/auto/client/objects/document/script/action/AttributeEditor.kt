package tech.kzen.auto.client.objects.document.script.action


import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.notation.edit.UpsertAttributeCommand
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList


// TODO: inject type-based editor
class AttributeEditor(
        props: Props
):
        RPureComponent<AttributeEditor.Props, AttributeEditor.State>(props),
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectLocation: ObjectLocation,
            var attributeName: AttributeName,

            var value: String?,
            var values: List<String>?
    ): RProps


    class State(
            var value: String?,
            var values: List<String>?,

            var submitDebounce: FunctionWithDebounce,
            var pending: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")
        if (props.value == null) {
            value = null
            values = props.values!!
        }
        else {
            value = props.value
            values = null
        }


        submitDebounce = lodash.debounce({
            editParameterCommandAsync()
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
    suspend fun flush() {
//        println("ParameterEditor | flush")

        state.submitDebounce.cancel()
        if (state.pending) {
            editParameterCommand()
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



    private fun editParameterCommandAsync() {
        async {
            editParameterCommand()
        }
    }


    private suspend fun editParameterCommand() {
        if (state.value != null) {
            ClientContext.commandBus.apply(UpsertAttributeCommand(
                    props.objectLocation,
                    props.attributeName,
                    ScalarAttributeNotation(state.value!!)))
        }
        else {
            ClientContext.commandBus.apply(UpsertAttributeCommand(
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
        if (state.value != null) {
            renderString(state.value!!)
        }
        else {
            renderListOfString(state.values!!)
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
