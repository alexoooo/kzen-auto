package tech.kzen.auto.client.objects.document.report.formula

import kotlinx.css.*
import org.w3c.dom.HTMLTextAreaElement
import react.*
import styled.css
import styled.styledDiv
import styled.styledPre
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


class ReportFormulaItem(
    props: Props
):
    RPureComponent<ReportFormulaItem.Props, ReportFormulaItem.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var formulaSpec: FormulaSpec
        var columnName: String
    }


    interface State: RState {
//        var open: Boolean
        var removeError: String?
        var updateError: String?
        var value: String
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            onSubmitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        open = false
        removeError = null
        updateError = null
        value = props.formulaSpec.formulas[props.columnName]!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSubmitEdit() {
        val formula = props.formulaSpec.formulas[props.columnName]
            ?: return

        if (state.value == formula) {
            return
        }

        val updateRequest = FormulaValueUpdateRequest(
            props.columnName, state.value)

        props.dispatcher.dispatchAsync(
            CompoundReportAction(
                updateRequest,
                FormulaValidationRequest,
                ReportEffect.refreshView))
    }


    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }
        submitDebounce.apply()
    }


    private fun onDelete() {
        if (state.removeError != null) {
            setState {
                updateError = null
            }
        }

        async {
            val effects = props.dispatcher.dispatch(
                FormulaRemoveRequest(props.columnName))

            val effect = effects.filterIsInstance<FormulaUpdateResult>().first()

            if (effect.errorMessage != null) {
                setState {
                    removeError = effect.errorMessage
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val columnCriteria = props.formulaSpec.columns[props.columnName]
//            ?: return

        val editDisabled =
            props.reportState.isInitiating() ||
            props.reportState.taskRunning

        val error: String? =
            props.reportState.formulaMessages[props.columnName]

        child(MaterialIconButton::class) {
            attrs {
                style = reactStyle {
                    marginLeft = 0.25.em
                    verticalAlign = VerticalAlign.top
                }

                onClick = {
                    onDelete()
                }

                disabled = editDisabled
            }

            child(DeleteIcon::class) {}
        }

        styledDiv {
            css {
                width = 40.em
                display = Display.inlineBlock
            }

            renderEditor(editDisabled, error != null)

            if (error != null) {
                styledPre {
                    +error
                }
            }
        }
    }


    private fun RBuilder.renderEditor(
        editDisabled: Boolean,
        hasError: Boolean
    ) {
        child(MaterialTextField::class) {
            attrs {
                fullWidth = true
                multiline = true

                label = props.columnName
                value = state.value

                onChange = {
                    val value = (it.target as HTMLTextAreaElement).value
                    onValueChange(value)
                }

                disabled = editDisabled
                error = hasError
            }
        }
    }
}