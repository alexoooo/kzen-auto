package tech.kzen.auto.client.objects.document.report.formula

import kotlinx.css.*
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.report.state.FormulaAddRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.CancelIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


class ReportFormulaAdd(
    props: Props
):
    RPureComponent<ReportFormulaAdd.Props, ReportFormulaAdd.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher,
        var formulaSpec: FormulaSpec
    ): RProps


    class State(
        var adding: Boolean,
        var selectedColumn: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        adding = false
        selectedColumn = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd() {
        setState {
            adding = true
            selectedColumn = null
        }
    }


    private fun onCancel() {
        setState {
            adding = false
//            selectedColumn = null
        }
    }


    private fun onSubmit() {
        val columnName = state.selectedColumn
            ?: return

        props.dispatcher.dispatchAsync(
            FormulaAddRequest(columnName))

        setState {
            adding = false
            selectedColumn = null
        }
    }


    private fun onValueChange(columnName: String) {
        setState {
            selectedColumn = columnName
        }
    }


    private fun handleEnterAndEscape(event: KeyboardEvent) {
//        console.log("event.key: ${event.key}", event)

        when (event.key) {
            "Enter" -> onSubmit()
            "Escape" -> onCancel()
            else -> return
        }

        event.preventDefault()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val columnListing = props.reportState.columnListing

        val editDisabled =
            props.reportState.isInitiating() ||
            props.reportState.taskRunning

        styledDiv {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (! props.reportState.formulaLoading &&
                    props.reportState.formulaError != null
            ) {
                styledDiv {
                    +"Error: ${props.reportState.formulaError}"
                }
            }

            if (state.adding) {
                styledDiv {
                    css {
                        display = Display.inlineBlock
                        width = 15.em
                    }

                    renderName(columnListing, editDisabled)
                }

                renderCancelAndSubmit()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun RBuilder.renderAddButton() {
        styledDiv {
            attrs {
                title = "Add column filter"
            }

            css {
                display = Display.inlineBlock
            }

            child(MaterialIconButton::class) {
                attrs {
                    onClick = {
                        onAdd()
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderCancelAndSubmit() {
        styledDiv {
            css {
                display = Display.inlineBlock
                marginTop = 0.5.em
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Add new calculated column"
                    onClick = {
                        onSubmit()
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Cancel adding calculated column"
                    onClick = {
                        onCancel()
                    }
//                    disabled = props.reportState.formulaLoading
                }

                child(CancelIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderName(
        columnListing: List<String>?,
        editDisabled: Boolean
    ) {
        child(MaterialTextField::class) {
            attrs {
                label = "Calculated column name"
                fullWidth = true

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                disabled = editDisabled
                error = ! (columnListing?.contains(state.selectedColumn) ?: false)

                onKeyDown = ::handleEnterAndEscape
            }
        }
    }
}