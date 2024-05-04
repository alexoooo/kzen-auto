package tech.kzen.auto.client.objects.document.report.formula

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.ReactNode
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.react
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaState
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import web.cssom.Display
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaAddControllerProps: react.Props {
    var formulaSpec: FormulaSpec
    var formulaState: ReportFormulaState
    var inputColumns: HeaderListing?
    var runningOrLoading: Boolean
    var formulaStore: ReportFormulaStore
}


external interface FormulaAddControllerState: react.State {
    var adding: Boolean
    var selectedColumn: String?
}


//---------------------------------------------------------------------------------------------------------------------
class FormulaAddController(
    props: FormulaAddControllerProps
):
    RPureComponent<FormulaAddControllerProps, FormulaAddControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun FormulaAddControllerState.init(props: FormulaAddControllerProps) {
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
        }
    }


    private fun onSubmit() {
        val columnName = state.selectedColumn
            ?: return

        props.formulaStore.addFormulaAsync(columnName)

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


    private fun handleEnterAndEscape(event: KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onSubmit, ::onCancel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (! props.formulaState.formulaLoading &&
                    props.formulaState.formulaError != null
            ) {
                div {
                    +"Error: ${props.formulaState.formulaError}"
                }
            }

            if (state.adding) {
                div {
                    css {
                        display = Display.inlineBlock
                        width = 15.em
                    }

                    renderName()
                }

                renderCancelAndSubmit()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = "Add column filter"

            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onAdd()
                }

                AddCircleOutlineIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderCancelAndSubmit() {
        div {
            css {
                display = Display.inlineBlock
                marginTop = 0.5.em
            }

            IconButton {
                title = "Add new calculated column"
                onClick = {
                    onSubmit()
                }
                AddCircleOutlineIcon::class.react {}
            }

            IconButton {
                title = "Cancel adding calculated column"
                onClick = {
                    onCancel()
                }
                CancelIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderName() {
        TextField {
            label = ReactNode("Calculated column name")
            fullWidth = true
            size = Size.small

            onChange = {
                val target = it.target as HTMLInputElement
                onValueChange(target.value)
            }

            disabled = props.runningOrLoading
            error = (props.inputColumns?.values?.any { it.text == state.selectedColumn } ?: false)

            onKeyDown = {e ->
                handleEnterAndEscape(e)
            }
        }
    }
}