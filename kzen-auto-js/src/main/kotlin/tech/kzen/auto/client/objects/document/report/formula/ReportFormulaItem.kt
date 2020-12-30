package tech.kzen.auto.client.objects.document.report.formula

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import styled.styledPre
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.DeleteIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


class ReportFormulaItem(
    props: Props
):
    RPureComponent<ReportFormulaItem.Props, ReportFormulaItem.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher,
        var formulaSpec: FormulaSpec,
        var columnName: String
    ): RProps


    class State(
        var open: Boolean,
        var removeError: String?,
        var updateError: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        open = false
        removeError = null
        updateError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
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
    private fun onOpenToggle() {
        setState {
            open = ! open
        }
    }


    private fun onChangedByEdit() {
        props.dispatcher.dispatchAsync(
            CompoundReportAction(
                FormulaValidationRequest,
                ReportEffect.refreshView))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val columnCriteria = props.formulaSpec.columns[props.columnName]
//            ?: return

        val reportState = props.reportState

        val editDisabled =
            reportState.initiating ||
            reportState.taskRunning ||
            reportState.formulaLoading

        val error: String? =
            reportState.formulaMessages[props.columnName]

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

            child(AttributePathValueEditor::class) {
                attrs {
                    disabled = editDisabled
                    multilineOverride = true
                    invalid = error != null
                    labelOverride = props.columnName

                    clientState = props.reportState.clientState
                    objectLocation = props.reportState.mainLocation

                    attributePath = FormulaSpec.formulaAttributePath(props.columnName)

                    valueType = TypeMetadata.string

                    onChange = {
                        onChangedByEdit()
                    }
                }
            }

            if (error != null) {
                styledPre {
                    +error
                }
            }
        }
    }
}