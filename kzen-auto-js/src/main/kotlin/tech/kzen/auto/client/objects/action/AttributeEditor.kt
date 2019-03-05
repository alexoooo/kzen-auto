package tech.kzen.auto.client.objects.action


import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.notation.edit.UpsertAttributeCommand
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


// TODO: inject type-based editor
class AttributeEditor(
        props: AttributeEditor.Props
):
        RComponent<AttributeEditor.Props, AttributeEditor.State>(props),
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


//            var submitDebounce: (Unit) -> Unit
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
            ClientContext.executionManager.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.executionManager.unsubscribe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(objectLocation: ObjectLocation) {
        flush()
    }


    override suspend fun onExecutionModel(executionModel: ExecutionModel) {}


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
                    ScalarAttributeNotation(state.value)))
        }
        else {
            ClientContext.commandBus.apply(UpsertAttributeCommand(
                    props.objectLocation,
                    props.attributeName,
                    ListAttributeNotation(
                            state.values!!.map { ScalarAttributeNotation(it) }
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
