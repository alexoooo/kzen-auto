package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.css.em
import kotlinx.css.marginTop
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import tech.kzen.auto.client.objects.document.report.state.InputsSelectionGroupByRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.reactStyle


class InputSelectedGroup(
    props: Props
):
    RPureComponent<InputSelectedGroup.Props, InputSelectedGroup.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: RState {
        var groupByText: String
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        groupByText = props.reportState.inputSpec().selection.groupBy
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
        if (props.reportState.inputSpec().selection.groupBy == state.groupByText) {
            return
        }

        props.dispatcher.dispatchAsync(InputsSelectionGroupByRequest(state.groupByText))
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
                style = reactStyle {
                    marginTop = 0.25.em
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