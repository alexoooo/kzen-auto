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
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import web.cssom.Display
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface AddNameFormProps: Props {
    // What is being added, in the user's own words ("sort key", "calculated column", "column filter") — the
    // buttons read "Add <entityLabel>" and "Cancel adding <entityLabel>".
    var entityLabel: String

    // Floating label of the name field. Separate from [entityLabel] because the name is not always the entity:
    // a "sort key" is added by typing a "Sort column name".
    var fieldLabel: String

    // Whether a typed name is already taken — the field turns red and the submit is refused.
    var isDuplicate: (String) -> Boolean

    // The trimmed name that was typed, or the `value` of the option that was picked.
    var onAdd: (String) -> Unit

    // Non-empty offers a pick-list in place of the free-text field, for a caller that already knows the names
    // available. Picking IS the submit, so that path has no confirm button.
    var options: Array<SelectOption>?
}


external interface AddNameFormState: State {
    var adding: Boolean
    var name: String
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * The "add one by name" affordance the Worker attribute editors share: a button that expands into a name field
 * with confirm / cancel, and collapses again on either. The half-typed name is local to this component, so the
 * parent re-rendering (a store refresh, a sibling edit) cannot disturb it.
 */
class AddNameForm(
    props: AddNameFormProps
):
    RPureComponent<AddNameFormProps, AddNameFormState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun AddNameFormState.init(props: AddNameFormProps) {
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
        if (trimmed.isEmpty() || props.isDuplicate(trimmed)) {
            return
        }

        add(trimmed)
    }


    private fun onPick(option: SelectOption) {
        add(option.value)
    }


    private fun add(value: String) {
        props.onAdd(value)

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
            val options = props.options

            if (!state.adding) {
                renderAddButton()
            }
            else if (options.isNullOrEmpty()) {
                renderName()
                renderSubmitAndCancel()
            }
            else {
                renderPicker(options)
                renderCancel()
            }
        }
    }


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = "Add ${props.entityLabel}"
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
                label = ReactNode(props.fieldLabel)
                fullWidth = true
                size = Size.small

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                // An empty field is not yet a duplicate of anything.
                error = state.name.trim().let { it.isNotEmpty() && props.isDuplicate(it) }

                onKeyDown = { e ->
                    handleEnterAndEscape(e)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderPicker(options: Array<SelectOption>) {
        div {
            css {
                display = Display.inlineBlock
                width = 15.em
            }

            muiAutocompleteField(
                label = props.fieldLabel,
                options = options,
                selectedOption = null,
                onSelect = { onPick(it) },
                disableClearable = true,
                autoFocus = true,
                openOnFocus = true)
        }
    }


    private fun ChildrenBuilder.renderSubmitAndCancel() {
        div {
            css {
                display = Display.inlineBlock
            }

            IconButton {
                title = "Add ${props.entityLabel}"
                onClick = {
                    onSubmit()
                }
                icon("material-symbols:add-circle-outline") {}
            }

            renderCancelButton()
        }
    }


    private fun ChildrenBuilder.renderCancel() {
        div {
            css {
                display = Display.inlineBlock
            }

            renderCancelButton()
        }
    }


    private fun ChildrenBuilder.renderCancelButton() {
        IconButton {
            title = "Cancel adding ${props.entityLabel}"
            onClick = {
                onCancel()
            }
            icon("material-symbols:cancel") {}
        }
    }
}
