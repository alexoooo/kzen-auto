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
import tech.kzen.lib.common.edit.EditParameterCommand
import tech.kzen.lib.common.notation.model.ScalarParameterNotation


@Suppress("unused")
class ParameterEditor(
        props: ParameterEditor.Props
) :
        RComponent<ParameterEditor.Props, ParameterEditor.State>(props),
        ExecutionManager.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectName: String,
            var parameterPath: String,
            var value: String
    ) : RProps


    class State(
            var value: String,
//            var submitDebounce: (Unit) -> Unit
            var submitDebounce: FunctionWithDebounce,
            var pending: Boolean
    ) : RState


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
    override suspend fun beforeExecution(executionModel: ExecutionModel) {
        flush()
    }


    override suspend fun afterExecution(executionModel: ExecutionModel) {}


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
        ClientContext.commandBus.apply(EditParameterCommand(
                props.objectName,
                props.parameterPath,
                ScalarParameterNotation(state.value)))

        setState {
            pending = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true

                label = props.parameterPath
                value = state.value

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }
            }
        }
    }
}
