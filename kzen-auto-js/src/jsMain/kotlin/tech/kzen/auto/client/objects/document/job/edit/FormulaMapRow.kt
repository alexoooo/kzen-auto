package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.dom.onChange
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.inputLabelSlotProps
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.setState
import web.cssom.VerticalAlign
import web.cssom.em
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaMapRowProps: Props {
    var columnName: String
    var formula: String
    var onUpdate: (String, String) -> Unit
    var onDelete: (String) -> Unit
}


external interface FormulaMapRowState: State {
    var value: String
}


//---------------------------------------------------------------------------------------------------------------------
// One calculated-column entry of a FormulaWorker's `formula` map: a multiline Kotlin-expression field plus a delete
// button. Owns its in-progress text and debounces the update command (so typing doesn't fire a command per
// keystroke), flushing on blur and unmount so a pending edit is committed rather than discarded. The committed value
// arrives via [FormulaMapRowProps.formula]; the row is keyed by column name so its local text survives the parent's
// re-read of the map.
class FormulaMapRow(
    props: FormulaMapRowProps
):
    RPureComponent<FormulaMapRowProps, FormulaMapRowState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            onSubmitEdit()
        }
    }, 1_000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun FormulaMapRowState.init(props: FormulaMapRowProps) {
        value = props.formula
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSubmitEdit() {
        if (state.value == props.formula) {
            return
        }
        props.onUpdate(props.columnName, state.value)
    }


    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }
        submitDebounce.apply()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentWillUnmount() {
        submitDebounce.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        table {
            css {
                marginLeft = 0.25.em
            }
            tbody {
                tr {
                    td {
                        IconButton {
                            sx {
                                verticalAlign = VerticalAlign.top
                            }
                            onClick = {
                                props.onDelete(props.columnName)
                            }
                            icon("material-symbols:delete") {}
                        }
                    }

                    td {
                        css {
                            width = 30.em
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

                            // Commit the pending debounced edit on focus loss, so a following separate command
                            // is sequenced after this write rather than racing it.
                            onBlur = { submitDebounce.flush() }

                            inputLabelSlotProps = unsafeJso {
                                shrink = true
                            }
                        }
                    }
                }
            }
        }
    }
}
