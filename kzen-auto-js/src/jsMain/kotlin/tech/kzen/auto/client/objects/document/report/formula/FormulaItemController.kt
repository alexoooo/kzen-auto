package tech.kzen.auto.client.objects.document.report.formula

import emotion.react.css
import js.objects.jso
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.ReactNode
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.dom.onChange
import react.react
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaState
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaStore
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.util.ExpressionUtils
import web.cssom.VerticalAlign
import web.cssom.em
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaItemControllerProps: react.Props {
    var formulaState: ReportFormulaState
    var formulaSpec: FormulaSpec
    var runningOrLoading: Boolean
    var inputColumns: HeaderListing?
    var columnName: String
    var formulaStore: ReportFormulaStore
}


external interface FormulaItemControllerState: react.State {
    var value: String
}


//---------------------------------------------------------------------------------------------------------------------
class FormulaItemController(
    props: FormulaItemControllerProps
):
    RPureComponent<FormulaItemControllerProps, FormulaItemControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            onSubmitEdit()
        }
    }, 1_000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun FormulaItemControllerState.init(props: FormulaItemControllerProps) {
        value = props.formulaSpec.formulas[props.columnName]!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSubmitEdit() {
        val formula = props.formulaSpec.formulas[props.columnName]
            ?: return

        if (state.value == formula) {
            return
        }

        submitValue(state.value)
    }


    private fun submitValue(newValue: String) {
        props.formulaStore.updateFormulaAsync(props.columnName, newValue)
    }


    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }
        submitDebounce.apply()
    }


    private fun onDelete() {
        props.formulaStore.removeFormulaAsync(props.columnName)
    }


    private fun onInsertColumn(columnName: HeaderLabel) {
        val escaped = ExpressionUtils.escapeKotlinVariableName(columnName)

        val escapePrefix =
            if (state.value.isEmpty() || state.value.endsWith(" ")) {
                ""
            }
            else {
                " "
            }

        val valueSuffix = escapePrefix + escaped
        val newValue = state.value + valueSuffix

        setState {
            value = newValue
        }

        submitDebounce.cancel()
        submitValue(newValue)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val columnCriteria = props.formulaSpec.columns[props.columnName]
//            ?: return

//        val editDisabled =
//            props.reportState.isInitiating() ||
//            props.reportState.taskRunning

        val error: String? =
            props.formulaState.formulaMessages[props.columnName]

        table {
            css {
                marginLeft = 0.25.em
            }
            tbody {
                tr {
                    td {
                        IconButton {
                            css {
                                verticalAlign = VerticalAlign.top
                            }

                            onClick = {
                                onDelete()
                            }

                            disabled = props.runningOrLoading

                            DeleteIcon::class.react {}
                        }
                    }

                    td {
                        css {
                            width = 40.em
                        }
                        TextField {
                            fullWidth = true
                            multiline = true
                            size = Size.small

                            label = ReactNode(props.columnName)
                            value = state.value

                            onChange = {
                                val value = (it.target as HTMLTextAreaElement).value
                                onValueChange(value)
                            }

                            disabled = props.runningOrLoading
                            this.error = error != null

                            InputLabelProps = jso {
                                shrink = true
                            }
                        }

                        if (error != null) {
                            div {
                                pre {
                                    +error
                                }
                            }
                        }
                    }

                    td {
                        FormulaReferenceController::class.react {
                            this.inputColumns = props.inputColumns ?: HeaderListing.empty
                            this.editDisabled = props.runningOrLoading

                            addLabel = "Column reference"
                            addIcon = "CallReceived"

                            onAdded = { columnName ->
                                onInsertColumn(columnName)
                            }
                        }
                    }
                }
            }
        }
    }
}