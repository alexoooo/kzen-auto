package tech.kzen.auto.client.ui


import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.get
import react.*
import react.dom.div
import react.dom.input
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.lib.common.edit.EditParameterCommand
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import kotlin.browser.window


@Suppress("unused")
class ParameterEditor(
        props: ParameterEditor.Props
) : RComponent<ParameterEditor.Props, ParameterEditor.State>(props) {

    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectName: String,
            var parameterPath: String,
            var value: String
    ) : RProps


    class State(
            var value: String,
            var submitDebounce: (Unit) -> Unit
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")
        value = props.value

        submitDebounce = lodash.debounce<Unit, Unit>({
//            console.log("debounce")
            onSubmit()
        }, 650)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }

        console.log("onValueChange")
        state.submitDebounce.invoke(Unit)
    }


    private fun onSubmit() {
//        console.log("ParameterEditor.onSubmit")

        editParameter()
    }


    private fun editParameter() {
        async {
            ClientContext.commandBus.apply(EditParameterCommand(
                    props.objectName,
                    props.parameterPath,
                    ScalarParameterNotation(state.value)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        div {
            +("[${props.parameterPath}]: ")

            input (type = InputType.text) {
                attrs {
                    value = state.value

                    onChangeFunction = {
                        val target = it.target as HTMLInputElement
                        onValueChange(target.value)
                    }
                }
            }

//            input (type = InputType.button) {
//                attrs {
//                    value = "Edit"
//                    onClickFunction = {
//                        onSubmit()
//                    }
//                }
//            }
        }
    }
}
