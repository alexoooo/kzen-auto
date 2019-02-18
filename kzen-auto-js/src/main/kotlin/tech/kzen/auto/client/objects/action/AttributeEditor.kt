package tech.kzen.auto.client.objects.action


import org.w3c.dom.HTMLInputElement
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.notation.edit.UpsertAttributeCommand
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


@Suppress("unused")
class AttributeEditor(
        props: AttributeEditor.Props
):
        RComponent<AttributeEditor.Props, AttributeEditor.State>(props),
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectName: ObjectLocation,
            var attributeName: AttributeName,
            var value: String
    ): RProps


    class State(
            var value: String,
//            var submitDebounce: (Unit) -> Unit
            var submitDebounce: FunctionWithDebounce,
            var pending: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")
        value = props.value

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


    private fun onSubmit() {
        editParameterCommandAsync()
    }


    private fun editParameterCommandAsync() {
        async {
            editParameterCommand()
        }
    }


    private suspend fun editParameterCommand() {
        ClientContext.commandBus.apply(UpsertAttributeCommand(
                props.objectName,
                props.attributeName,
                ScalarAttributeNotation(state.value)))

        setState {
            pending = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true

                label = props.attributeName.value
                value = state.value

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }
            }
        }
    }
}
