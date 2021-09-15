package tech.kzen.auto.client.objects.document.pipeline.formula

import kotlinx.css.*
import org.w3c.dom.HTMLTextAreaElement
import react.RBuilder
import react.RPureComponent
import react.dom.ReactHTML.tbody
import react.dom.ReactHTML.td
import react.dom.ReactHTML.tr
import react.dom.div
import react.setState
import styled.css
import styled.styledPre
import styled.styledTable
import styled.styledTd
import tech.kzen.auto.client.objects.document.pipeline.formula.model.PipelineFormulaState
import tech.kzen.auto.client.objects.document.pipeline.formula.model.PipelineFormulaStore
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.material.NestedInputLabelProps
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


class FormulaItemController(
    props: Props
):
    RPureComponent<FormulaItemController.Props, FormulaItemController.State>(props)
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
    interface Props: react.Props {
        var formulaState: PipelineFormulaState
        var formulaSpec: FormulaSpec
        var runningOrLoading: Boolean
        var columnName: String
        var formulaStore: PipelineFormulaStore
    }


    interface State: react.State {
//        var open: Boolean
//        var removeError: String?
//        var updateError: String?
        var value: String
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            onSubmitEdit()
        }
    }, 1_000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        open = false
//        removeError = null
//        updateError = null
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
    override fun RBuilder.render() {
//        val columnCriteria = props.formulaSpec.columns[props.columnName]
//            ?: return

//        val editDisabled =
//            props.reportState.isInitiating() ||
//            props.reportState.taskRunning

        val error: String? =
            props.formulaState.formulaMessages[props.columnName]

        styledTable {
            css {
                marginLeft = 0.25.em
            }
            tbody {
                tr {
                    td {
                        child(MaterialIconButton::class) {
                            attrs {
                                style = reactStyle {
                                    verticalAlign = VerticalAlign.top
                                }

                                onClick = {
                                    onDelete()
                                }

                                disabled = props.runningOrLoading
                            }

                            child(DeleteIcon::class) {}
                        }
                    }

                    styledTd {
                        css {
                            width = 40.em
                        }
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

                                disabled = props.runningOrLoading
                                this.error = error != null

                                InputLabelProps = NestedInputLabelProps(shrink = true)
                            }
                        }

                        if (error != null) {
                            div {
                                styledPre {
                                    +error
                                }
                            }
                        }
                    }

                    td {
                        +"[ref]"
//                        child(FormulaColumnReference::class) {
//                            attrs {
//                                this.columnNames = props.reportState.columnListing ?: listOf()
//                                this.editDisabled = editDisabled
//
//                                addLabel = "Column reference"
//                                addIcon = "CallReceived"
//
//                                onAdded = { columnName ->
//                                    onInsertColumn(columnName)
//                                }
//                            }
//                        }
                    }
                }
            }
        }
    }
}