package tech.kzen.auto.client.objects.document.report.formula

import csstype.VerticalAlign
import csstype.em
import emotion.react.css
import js.core.jso
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
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaItemControllerProps: react.Props {
    var formulaState: ReportFormulaState
    var formulaSpec: FormulaSpec
    var runningOrLoading: Boolean
    var inputColumns: List<String>?
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
    companion object {
        // https://stackoverflow.com/a/44149580/1941359
        private val reservedWords = setOf(
            "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
            "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
            "if", "try", "else", "while", "do", "when", "interface", "typeof")

        private val simpleVariablePattern = Regex("[a-zA-Z][a-zA-Z0-9_]+")

        private fun escapeColumnName(columnName: String): String {
            if (columnName in reservedWords) {
                return backticksQuote(columnName)
            }

            if (simpleVariablePattern.matches(columnName)) {
                return columnName
            }

            return backticksQuote(columnName)
        }

        private fun backticksQuote(identifier: String): String {
            return "`$identifier`"
        }
    }


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


    private fun onInsertColumn(columnName: String) {
        val escaped = escapeColumnName(columnName)

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
            value += valueSuffix
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
                            this.inputColumns = props.inputColumns ?: listOf()
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