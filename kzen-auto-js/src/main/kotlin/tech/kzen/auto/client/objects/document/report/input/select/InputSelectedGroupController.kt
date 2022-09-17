package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.css.em
import kotlinx.css.marginTop
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec


//---------------------------------------------------------------------------------------------------------------------
external interface InputSelectedGroupControllerProps: Props {
    var spec: InputSelectionSpec
    var editDisabled: Boolean
    var inputStore: ReportInputStore
}


external interface InputSelectedGroupControllerState: State {
    var groupByText: String
}


//---------------------------------------------------------------------------------------------------------------------
class InputSelectedGroupController(
    props: InputSelectedGroupControllerProps
):
    RPureComponent<InputSelectedGroupControllerProps, InputSelectedGroupControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun InputSelectedGroupControllerState.init(props: InputSelectedGroupControllerProps) {
        groupByText = props.spec.groupBy
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            groupByText = newValue
        }
        submitDebounce.apply()
    }


    private fun submitEdit() {
        if (props.spec.groupBy == state.groupByText) {
            return
        }

        props.inputStore.selected.groupByAsync(state.groupByText)
    }


    private fun handleEnter(event: KeyboardEvent) {
        ClientInputUtils.handleEnter(event) {
            submitDebounce.cancel()
            submitEdit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialTextField::class) {
            attrs {
//                variant = "standard"
//                variant = "filled"
                size = "small"
                style = reactStyle {
                    marginTop = 0.5.em
//                    marginTop = 0.25.em
                }

                label = "Group By (Regular Expression)"
                fullWidth = true

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                value = state.groupByText
                disabled = props.editDisabled
                onKeyDown = ::handleEnter
            }
        }
    }
}