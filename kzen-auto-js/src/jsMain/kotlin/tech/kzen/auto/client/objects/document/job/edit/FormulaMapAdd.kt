package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import web.cssom.Display
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaMapAddProps: Props {
    var existingNames: Set<String>
    var onAdd: (String) -> Unit
}


external interface FormulaMapAddState: State {
    var adding: Boolean
    var name: String
}


//---------------------------------------------------------------------------------------------------------------------
// The "add calculated column" affordance of the FormulaWorker `formula` editor: a button that expands to a
// column-name field with confirm / cancel. Names must be non-empty and unique within the map; the new entry starts
// with an empty expression (the corresponding row's text field then edits it).
class FormulaMapAdd(
    props: FormulaMapAddProps
):
    RPureComponent<FormulaMapAddProps, FormulaMapAddState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun FormulaMapAddState.init(props: FormulaMapAddProps) {
        adding = false
        name = ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddClick() {
        setState {
            adding = true
            name = ""
        }
    }


    private fun onCancel() {
        setState {
            adding = false
            name = ""
        }
    }


    private fun onSubmit() {
        val trimmed = state.name.trim()
        if (trimmed.isEmpty() || trimmed in props.existingNames) {
            return
        }

        props.onAdd(trimmed)

        setState {
            adding = false
            name = ""
        }
    }


    private fun onValueChange(newName: String) {
        setState {
            name = newName
        }
    }


    private fun handleEnterAndEscape(event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onSubmit, ::onCancel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            if (state.adding) {
                renderName()
                renderCancelAndSubmit()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = "Add calculated column"
            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onAddClick()
                }
                icon("material-symbols:add-circle-outline") {}
            }
        }
    }


    private fun ChildrenBuilder.renderName() {
        div {
            css {
                display = Display.inlineBlock
                width = 15.em
            }

            TextField {
                label = ReactNode("Calculated column name")
                fullWidth = true
                size = Size.small

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                error = state.name.trim() in props.existingNames

                onKeyDown = { e ->
                    handleEnterAndEscape(e)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderCancelAndSubmit() {
        div {
            css {
                display = Display.inlineBlock
            }

            IconButton {
                title = "Add calculated column"
                onClick = {
                    onSubmit()
                }
                icon("material-symbols:add-circle-outline") {}
            }

            IconButton {
                title = "Cancel adding calculated column"
                onClick = {
                    onCancel()
                }
                icon("material-symbols:cancel") {}
            }
        }
    }
}
