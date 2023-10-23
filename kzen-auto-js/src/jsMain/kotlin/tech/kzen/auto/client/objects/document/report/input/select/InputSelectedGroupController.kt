package tech.kzen.auto.client.objects.document.report.input.select

import emotion.react.css
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.onChange
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import web.cssom.em
import web.html.HTMLInputElement


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


    private fun handleEnter(event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnter(event) {
            submitDebounce.cancel()
            submitEdit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        TextField {
            size = Size.small
            sx {
                marginTop = 0.5.em
            }

            label = ReactNode("Group By (Regular Expression)")
            fullWidth = true

            onChange = {
                val target = it.target as HTMLInputElement
                onValueChange(target.value)
            }

            value = state.groupByText
            disabled = props.editDisabled
            onKeyDown = { e -> handleEnter(e) }
        }
    }
}